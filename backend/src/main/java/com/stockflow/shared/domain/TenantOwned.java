package com.stockflow.shared.domain;

import java.util.UUID;

public interface TenantOwned {
    UUID id();

    UUID businessId();
}
