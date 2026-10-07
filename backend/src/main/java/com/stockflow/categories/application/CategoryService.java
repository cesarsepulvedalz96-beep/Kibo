package com.stockflow.categories.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.categories.domain.*;
import com.stockflow.shared.application.AuditService;
import com.stockflow.shared.domain.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryService {
    private final CategoryRepository categories;
    private final CurrentIdentity current;
    private final AuditService audit;

    public CategoryService(
            CategoryRepository categories, CurrentIdentity current, AuditService audit) {
        this.categories = categories;
        this.current = current;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<Category> search(String search, String status, int page, int size) {
        return categories.search(current.businessId(), search, status, page, size);
    }

    public Category save(UUID id, String name, boolean active) {
        current.requireManager();
        var previous =
                id == null
                        ? null
                        : categories
                                .lock(current.businessId(), id)
                                .orElseThrow(BusinessException::notFound);
        var category =
                categories.save(
                        new Category(
                                id == null ? UUID.randomUUID() : id,
                                current.businessId(),
                                name.trim(),
                                active,
                                previous == null ? Instant.now() : previous.createdAt(),
                                Instant.now()));
        audit.record(
                current.businessId(),
                current.userId(),
                "CATEGORY_SAVED",
                "category",
                category.id());
        return category;
    }
}
