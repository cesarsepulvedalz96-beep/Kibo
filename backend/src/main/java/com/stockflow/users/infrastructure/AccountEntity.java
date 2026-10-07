package com.stockflow.users.infrastructure;

import com.stockflow.users.domain.Account;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class AccountEntity {
    @Id private UUID id;
    private UUID businessId;
    private String email;
    private String displayName;
    private String passwordHash;
    private String role;
    private boolean active;
    private boolean mustChangePassword;
    private int accessVersion;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected AccountEntity() {}

    void update(Account account) {
        id = account.id();
        businessId = account.businessId();
        email = account.email();
        displayName = account.displayName();
        passwordHash = account.passwordHash();
        role = account.role();
        active = account.active();
        mustChangePassword = account.mustChangePassword();
        accessVersion = account.accessVersion();
        lastLoginAt = account.lastLoginAt();
        updatedAt = account.updatedAt();
        if (createdAt == null) createdAt = account.createdAt();
    }

    Account toDomain() {
        return new Account(
                id,
                businessId,
                email,
                displayName,
                passwordHash,
                role,
                active,
                mustChangePassword,
                accessVersion,
                lastLoginAt,
                createdAt,
                updatedAt);
    }
}
