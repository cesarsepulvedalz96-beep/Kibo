package com.stockflow.auth.infrastructure;

import com.stockflow.auth.application.AuthService;
import com.stockflow.auth.domain.SessionIdentity;
import com.stockflow.billing.domain.SubscriptionAccessPolicy;
import com.stockflow.shared.domain.BusinessException;
import com.stockflow.users.domain.AccountRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActiveIdentityFilter extends OncePerRequestFilter {
    private final AccountRepository accounts;
    private final AuthService auth;
    private final SubscriptionAccessPolicy subscriptions;

    public ActiveIdentityFilter(
            AccountRepository accounts, AuthService auth, SubscriptionAccessPolicy subscriptions) {
        this.accounts = accounts;
        this.auth = auth;
        this.subscriptions = subscriptions;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof SessionIdentity identity) {
            var account = accounts.find(identity.userId());
            boolean valid =
                    account.filter(
                                    stored ->
                                            auth.isEnabled(stored)
                                                    && stored.accessVersion()
                                                            == identity.accessVersion()
                                                    && stored.role().equals(identity.role())
                                                    && java.util.Objects.equals(
                                                            stored.businessId(),
                                                            identity.businessId()))
                            .isPresent();
            if (!valid) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"SESSION_EXPIRED\"}");
                return;
            }
            if (account.orElseThrow().mustChangePassword() && !passwordChangeAllowed(request)) {
                response.setStatus(403);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"PASSWORD_CHANGE_REQUIRED\"}");
                return;
            }
            if (identity.businessId() != null && !subscriptionAccessAllowed(request)) {
                try {
                    subscriptions.requireCommercialAccess(identity.businessId());
                } catch (BusinessException exception) {
                    response.setStatus(exception.status());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"" + exception.getMessage() + "\"}");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private boolean passwordChangeAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/me")
                || path.equals("/api/v1/auth/csrf")
                || path.equals("/api/v1/auth/logout")
                || path.equals("/api/v1/auth/change-password");
    }

    private boolean subscriptionAccessAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/me")
                || path.equals("/api/v1/auth/csrf")
                || path.equals("/api/v1/auth/logout")
                || path.equals("/api/v1/auth/change-password")
                || path.equals("/api/v1/subscription")
                || path.equals("/api/v1/events")
                || path.equals("/api/v1/subscription/payments")
                || path.equals("/api/v1/health");
    }
}
