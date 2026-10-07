package com.stockflow.users.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.businesses.domain.BusinessRepository;
import com.stockflow.shared.application.AuditService;
import com.stockflow.shared.domain.BusinessException;
import com.stockflow.shared.domain.Page;
import com.stockflow.users.domain.Account;
import com.stockflow.users.domain.AccountRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {
    private final AccountRepository accounts;
    private final BusinessRepository businesses;
    private final PasswordEncoder passwords;
    private final CurrentIdentity current;
    private final AuditService audit;
    private final Clock clock;

    public UserService(
            AccountRepository accounts,
            BusinessRepository businesses,
            PasswordEncoder passwords,
            CurrentIdentity current,
            AuditService audit,
            Clock clock) {
        this.accounts = accounts;
        this.businesses = businesses;
        this.passwords = passwords;
        this.current = current;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Account> list(UUID businessId, Boolean active, String search, int page, int size) {
        current.requireSuperAdmin();
        return accounts.list(businessId, active, search, page, size);
    }

    public Account create(UUID businessId, String email, String name, String temporaryPassword) {
        current.requireSuperAdmin();
        if (businessId == null || businesses.find(businessId).isEmpty()) {
            throw BusinessException.notFound();
        }
        validatePassword(temporaryPassword);
        Instant now = clock.instant();
        var account =
                accounts.save(
                        new Account(
                                UUID.randomUUID(),
                                businessId,
                                normalizeEmail(email),
                                normalizeName(name),
                                passwords.encode(temporaryPassword),
                                "BUSINESS_USER",
                                true,
                                true,
                                0,
                                null,
                                now,
                                now));
        audit.record(businessId, current.userId(), "USER_CREATED", "user", account.id());
        return account;
    }

    public Account edit(UUID id, String email, String name) {
        current.requireSuperAdmin();
        var previous = commercial(id);
        var account =
                accounts.save(
                        copy(
                                previous,
                                normalizeEmail(email),
                                normalizeName(name),
                                previous.passwordHash(),
                                previous.active(),
                                previous.mustChangePassword(),
                                previous.accessVersion(),
                                previous.lastLoginAt()));
        audit.record(account.businessId(), current.userId(), "USER_UPDATED", "user", account.id());
        return account;
    }

    public Account suspend(UUID id) {
        return setActive(id, false, "USER_SUSPENDED");
    }

    public Account reactivate(UUID id) {
        return setActive(id, true, "USER_REACTIVATED");
    }

    public Account resetPassword(UUID id, String temporaryPassword) {
        current.requireSuperAdmin();
        validatePassword(temporaryPassword);
        var previous = commercial(id);
        var account =
                accounts.save(
                        copy(
                                previous,
                                previous.email(),
                                previous.displayName(),
                                passwords.encode(temporaryPassword),
                                previous.active(),
                                true,
                                previous.accessVersion() + 1,
                                previous.lastLoginAt()));
        audit.record(
                account.businessId(),
                current.userId(),
                "USER_PASSWORD_RESET",
                "user",
                account.id());
        return account;
    }

    /** Changes the authenticated user's password and rotates every older session. */
    public Account changeOwnPassword(String newPassword) {
        validatePassword(newPassword);
        var previous = accounts.find(current.userId()).orElseThrow(BusinessException::notFound);
        var account =
                accounts.save(
                        copy(
                                previous,
                                previous.email(),
                                previous.displayName(),
                                passwords.encode(newPassword),
                                previous.active(),
                                false,
                                previous.accessVersion() + 1,
                                previous.lastLoginAt()));
        audit.record(
                account.businessId(), account.id(), "USER_PASSWORD_CHANGED", "user", account.id());
        return account;
    }

    private Account setActive(UUID id, boolean active, String action) {
        current.requireSuperAdmin();
        var previous = commercial(id);
        if (previous.active() == active) return previous;
        var account =
                accounts.save(
                        copy(
                                previous,
                                previous.email(),
                                previous.displayName(),
                                previous.passwordHash(),
                                active,
                                previous.mustChangePassword(),
                                previous.accessVersion() + 1,
                                previous.lastLoginAt()));
        audit.record(account.businessId(), current.userId(), action, "user", account.id());
        return account;
    }

    private Account commercial(UUID id) {
        var account = accounts.find(id).orElseThrow(BusinessException::notFound);
        if (!"BUSINESS_USER".equals(account.role())) throw new BusinessException("FORBIDDEN", 403);
        return account;
    }

    private Account copy(
            Account previous,
            String email,
            String name,
            String passwordHash,
            boolean active,
            boolean mustChangePassword,
            int accessVersion,
            Instant lastLoginAt) {
        return new Account(
                previous.id(),
                previous.businessId(),
                email,
                name,
                passwordHash,
                previous.role(),
                active,
                mustChangePassword,
                accessVersion,
                lastLoginAt,
                previous.createdAt(),
                clock.instant());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeName(String name) {
        return name.trim();
    }

    public static void validatePassword(String password) {
        if (password == null
                || password.length() < 12
                || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException("PASSWORD_POLICY", 400);
        }
    }
}
