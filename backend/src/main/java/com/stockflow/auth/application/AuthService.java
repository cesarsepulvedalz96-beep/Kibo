package com.stockflow.auth.application;

import com.stockflow.businesses.domain.BusinessRepository;
import com.stockflow.shared.domain.BusinessException;
import com.stockflow.users.domain.*;
import java.time.Clock;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {
    private final AccountRepository accounts;
    private final BusinessRepository businesses;
    private final PasswordEncoder passwords;
    private final String dummyHash;
    private final Clock clock;

    public AuthService(
            AccountRepository accounts,
            BusinessRepository businesses,
            PasswordEncoder passwords,
            Clock clock) {
        this.accounts = accounts;
        this.businesses = businesses;
        this.passwords = passwords;
        this.clock = clock;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    public Account authenticate(String email, String password) {
        var account = accounts.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        boolean matches =
                passwords.matches(password, account.map(Account::passwordHash).orElse(dummyHash));
        if (!matches || account.isEmpty() || !isEnabled(account.get())) {
            throw new BusinessException("INVALID_CREDENTIALS", 401);
        }
        var authenticated = account.get();
        var now = clock.instant();
        return accounts.save(
                new Account(
                        authenticated.id(),
                        authenticated.businessId(),
                        authenticated.email(),
                        authenticated.displayName(),
                        authenticated.passwordHash(),
                        authenticated.role(),
                        authenticated.active(),
                        authenticated.mustChangePassword(),
                        authenticated.accessVersion(),
                        now,
                        authenticated.createdAt(),
                        now));
    }

    public boolean isEnabled(Account account) {
        return account.active()
                && (account.businessId() == null
                        || businesses
                                .find(account.businessId())
                                .map(b -> b.active())
                                .orElse(false));
    }
}
