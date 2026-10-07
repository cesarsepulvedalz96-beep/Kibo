package com.stockflow.auth.infrastructure;

import com.stockflow.auth.application.AuthService;
import com.stockflow.billing.domain.SubscriptionAccessPolicy;
import com.stockflow.users.domain.AccountRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    org.springframework.security.core.userdetails.UserDetailsService userDetailsService(
            AccountRepository accounts) {
        return email -> {
            var account =
                    accounts.findByEmail(email.toLowerCase(java.util.Locale.ROOT))
                            .orElseThrow(
                                    () ->
                                            new org.springframework.security.core.userdetails
                                                    .UsernameNotFoundException(
                                                    "INVALID_CREDENTIALS"));
            return org.springframework.security.core.userdetails.User.withUsername(account.email())
                    .password(account.passwordHash())
                    .roles(account.role())
                    .disabled(!account.active())
                    .build();
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    DefaultCookieSerializer cookieSerializer(@Value("${app.cookie-secure}") boolean secure) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("STOCKFLOW_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AccountRepository accounts,
            AuthService auth,
            SubscriptionAccessPolicy subscriptions,
            SecurityContextRepository contexts,
            CsrfTokenRepository csrf,
            @Value("${app.trusted-proxies:}") String trustedProxies,
            @Value("${app.allowed-origin}") String allowedOrigin)
            throws Exception {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(allowedOrigin));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        cors.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);

        http.cors(config -> config.configurationSource(source))
                .csrf(config -> config.csrfTokenRepository(csrf))
                .securityContext(config -> config.securityContextRepository(contexts))
                .requestCache(config -> config.disable())
                .formLogin(config -> config.disable())
                .httpBasic(config -> config.disable())
                .logout(
                        config ->
                                config.logoutUrl("/api/v1/auth/logout")
                                        .deleteCookies("STOCKFLOW_SESSION")
                                        .logoutSuccessHandler(
                                                (request, response, authentication) ->
                                                        response.setStatus(204)))
                .authorizeHttpRequests(
                        config ->
                                config.requestMatchers(
                                                "/api/v1/auth/csrf",
                                                "/api/v1/auth/login",
                                                "/api/v1/health")
                                        .permitAll()
                                        .requestMatchers("/api/v1/admin/**")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers("/api/v1/auth/me")
                                        .authenticated()
                                        .requestMatchers("/api/v1/auth/change-password")
                                        .authenticated()
                                        .requestMatchers("/api/v1/**")
                                        .hasRole("BUSINESS_USER")
                                        .anyRequest()
                                        .denyAll())
                .exceptionHandling(
                        config ->
                                config.authenticationEntryPoint(
                                                (request, response, exception) -> {
                                                    response.setStatus(401);
                                                    response.setContentType("application/json");
                                                    response.getWriter()
                                                            .write("{\"code\":\"UNAUTHORIZED\"}");
                                                })
                                        .accessDeniedHandler(
                                                (request, response, exception) -> {
                                                    response.setStatus(403);
                                                    response.setContentType("application/json");
                                                    response.getWriter()
                                                            .write("{\"code\":\"FORBIDDEN\"}");
                                                }))
                .headers(
                        config ->
                                config.contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'none'; frame-ancestors 'none'"))
                                        .frameOptions(frame -> frame.deny())
                                        .referrerPolicy(
                                                referrer ->
                                                        referrer.policy(
                                                                org.springframework.security.web
                                                                        .header.writers
                                                                        .ReferrerPolicyHeaderWriter
                                                                        .ReferrerPolicy
                                                                        .NO_REFERRER)))
                .addFilterAfter(
                        new LoginRateLimiter(new TrustedClientAddress(trustedProxies)),
                        CsrfFilter.class)
                .addFilterBefore(
                        new ActiveIdentityFilter(accounts, auth, subscriptions),
                        AuthorizationFilter.class);
        return http.build();
    }
}
