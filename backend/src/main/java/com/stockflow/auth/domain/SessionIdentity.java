package com.stockflow.auth.domain;

import java.io.Serializable;
import java.util.UUID;

public record SessionIdentity(UUID userId, UUID businessId, String role, int accessVersion)
        implements Serializable, java.security.Principal {
    @Override
    public String getName() {
        return userId.toString();
    }
}
