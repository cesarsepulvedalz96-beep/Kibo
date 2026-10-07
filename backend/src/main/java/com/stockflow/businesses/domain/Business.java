package com.stockflow.businesses.domain;

import java.time.Instant;
import java.util.UUID;

public record Business(
        UUID id, String name, String taxId, boolean active, Instant createdAt, Instant updatedAt) {}
