package com.stockflow.billing.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.billing.domain.SubscriptionPlan;
import com.stockflow.billing.domain.SubscriptionPlanRepository;
import com.stockflow.shared.application.AuditService;
import com.stockflow.shared.domain.BusinessException;
import com.stockflow.shared.domain.Page;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubscriptionPlanService {
    private final SubscriptionPlanRepository plans;
    private final CurrentIdentity current;
    private final AuditService audit;
    private final Clock clock;

    public SubscriptionPlanService(
            SubscriptionPlanRepository plans,
            CurrentIdentity current,
            AuditService audit,
            Clock clock) {
        this.plans = plans;
        this.current = current;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionPlan> list(Boolean active, String search, int page, int size) {
        current.requireSuperAdmin();
        return plans.list(active, search, page, size);
    }

    public SubscriptionPlan save(
            UUID id,
            String name,
            String description,
            BigDecimal monthlyPrice,
            int billingPeriodDays,
            int gracePeriodDays,
            boolean active) {
        current.requireSuperAdmin();
        validate(monthlyPrice, billingPeriodDays, gracePeriodDays);
        var previous = id == null ? null : plans.find(id).orElseThrow(BusinessException::notFound);
        var now = clock.instant();
        var saved =
                plans.save(
                        new SubscriptionPlan(
                                id == null ? UUID.randomUUID() : id,
                                name.trim(),
                                description == null || description.isBlank()
                                        ? null
                                        : description.trim(),
                                monthlyPrice,
                                billingPeriodDays,
                                gracePeriodDays,
                                active,
                                previous == null ? now : previous.createdAt(),
                                now));
        audit.record(
                null,
                current.userId(),
                previous == null ? "SUBSCRIPTION_PLAN_CREATED" : "SUBSCRIPTION_PLAN_UPDATED",
                "subscription_plan",
                saved.id());
        return saved;
    }

    private static void validate(BigDecimal price, int periodDays, int graceDays) {
        if (price == null
                || price.signum() < 0
                || price.scale() > 2
                || periodDays < 1
                || periodDays > 3660
                || graceDays < 0
                || graceDays > 365) {
            throw new BusinessException("VALIDATION_ERROR", 400);
        }
    }
}
