package com.stockflow.settings.domain;

import java.util.UUID;

public interface SettingsRepository {
    BusinessSettings get(UUID businessId);

    BusinessSettings save(BusinessSettings settings);
}
