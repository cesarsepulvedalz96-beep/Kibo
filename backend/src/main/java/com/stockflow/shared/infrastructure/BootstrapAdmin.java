package com.stockflow.shared.infrastructure;

import com.stockflow.users.application.UserService;
import com.stockflow.users.domain.*;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdmin implements CommandLineRunner {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;
    private final Clock clock;

    public BootstrapAdmin(
            AccountRepository accounts,
            PasswordEncoder passwords,
            Clock clock,
            @Value("${app.bootstrap.email}") String email,
            @Value("${app.bootstrap.password}") String password) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.clock = clock;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (email.isBlank()
                || password.isBlank()
                || accounts.findByEmail(email.toLowerCase(Locale.ROOT)).isPresent()) return;
        // One-time bootstrap. Existing credentials are never overwritten at startup.
        UserService.validatePassword(password);
        var now = clock.instant();
        accounts.save(
                new Account(
                        UUID.randomUUID(),
                        null,
                        email.toLowerCase(Locale.ROOT),
                        "Administrador",
                        passwords.encode(password),
                        "SUPER_ADMIN",
                        true,
                        false,
                        0,
                        null,
                        now,
                        now));
    }
}
