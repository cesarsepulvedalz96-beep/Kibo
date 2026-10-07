package com.stockflow.auth.presentation;

import com.stockflow.auth.application.*;
import com.stockflow.auth.domain.SessionIdentity;
import com.stockflow.businesses.domain.BusinessRepository;
import com.stockflow.users.application.UserService;
import com.stockflow.users.domain.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final CurrentIdentity current;
    private final AccountRepository accounts;
    private final BusinessRepository businesses;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;
    private final UserService users;

    public AuthController(
            AuthService auth,
            CurrentIdentity current,
            AccountRepository accounts,
            BusinessRepository businesses,
            SecurityContextRepository contexts,
            CsrfTokenRepository csrf,
            UserService users) {
        this.auth = auth;
        this.current = current;
        this.accounts = accounts;
        this.businesses = businesses;
        this.contexts = contexts;
        this.csrf = csrf;
        this.users = users;
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 64) String password) {
        @Override
        public String toString() {
            return "LoginRequest[REDACTED]";
        }
    }

    public record CsrfResponse(String token, String headerName) {
        @Override
        public String toString() {
            return "CsrfResponse[REDACTED]";
        }
    }

    public record UserResponse(
            UUID id,
            UUID businessId,
            String email,
            String displayName,
            String role,
            String businessName,
            boolean mustChangePassword,
            Instant lastLoginAt) {}

    public record ChangePasswordRequest(@NotBlank @Size(max = 64) String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordRequest[REDACTED]";
        }
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/login")
    UserResponse login(
            @Valid @RequestBody LoginRequest input,
            HttpServletRequest request,
            HttpServletResponse response) {
        var account = auth.authenticate(input.email(), input.password());
        request.getSession();
        request.changeSessionId();
        var identity =
                new SessionIdentity(
                        account.id(),
                        account.businessId(),
                        account.role(),
                        account.accessVersion());
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        identity,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + account.role())));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        csrf.saveToken(null, request, response);
        return response(account);
    }

    @GetMapping("/me")
    UserResponse me() {
        return response(accounts.find(current.userId()).orElseThrow());
    }

    @PostMapping("/change-password")
    UserResponse changePassword(
            @Valid @RequestBody ChangePasswordRequest input,
            HttpServletRequest request,
            HttpServletResponse response) {
        var account = users.changeOwnPassword(input.newPassword());
        var identity =
                new SessionIdentity(
                        account.id(),
                        account.businessId(),
                        account.role(),
                        account.accessVersion());
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        identity,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + account.role())));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return response(account);
    }

    private UserResponse response(Account account) {
        String businessName =
                account.businessId() == null
                        ? "Kivo"
                        : businesses.find(account.businessId()).orElseThrow().name();
        return new UserResponse(
                account.id(),
                account.businessId(),
                account.email(),
                account.displayName(),
                account.role(),
                businessName,
                account.mustChangePassword(),
                account.lastLoginAt());
    }
}
