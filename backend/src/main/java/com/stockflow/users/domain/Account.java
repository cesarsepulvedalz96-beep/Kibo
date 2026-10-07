package com.stockflow.users.domain;

import java.time.Instant;
import java.util.UUID;

/** Credentials stay in the application layer; this model is never returned by a controller. */
public record Account(
        UUID id,
        UUID businessId,
        String email,
        String displayName,
        String passwordHash,
        String role,
        boolean active,
        boolean mustChangePassword,
        int accessVersion,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {
    @Override
    public String toString() {
        return "Account[id=" + id + ", credentials=REDACTED]";
    }
}
