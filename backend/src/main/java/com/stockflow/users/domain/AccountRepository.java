package com.stockflow.users.domain;

import com.stockflow.shared.domain.Page;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    Optional<Account> find(UUID id);

    Optional<Account> findByEmail(String email);

    Page<Account> list(UUID businessId, Boolean active, String search, int page, int size);

    Account save(Account account);
}
