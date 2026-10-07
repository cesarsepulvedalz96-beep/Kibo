package com.stockflow.businesses.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.billing.application.SubscriptionAdminService;
import com.stockflow.billing.domain.SubscriptionPlanRepository;
import com.stockflow.billing.domain.SubscriptionRepository;
import com.stockflow.businesses.domain.*;
import com.stockflow.settings.domain.*;
import com.stockflow.shared.application.AuditService;
import com.stockflow.shared.domain.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BusinessService {
    private final BusinessRepository businesses;
    private final SettingsRepository settings;
    private final CurrentIdentity current;
    private final AuditService audit;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final SubscriptionAdminService subscriptionAdmin;
    private final Clock clock;

    public BusinessService(
            BusinessRepository businesses,
            SettingsRepository settings,
            CurrentIdentity current,
            AuditService audit,
            SubscriptionRepository subscriptions,
            SubscriptionPlanRepository plans,
            SubscriptionAdminService subscriptionAdmin,
            Clock clock) {
        this.businesses = businesses;
        this.settings = settings;
        this.current = current;
        this.audit = audit;
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.subscriptionAdmin = subscriptionAdmin;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<BusinessView> list(int page, int size) {
        current.requireSuperAdmin();
        var result = businesses.list(page, size);
        return new Page<>(
                result.items().stream().map(this::view).toList(), result.total(), page, size);
    }

    public BusinessView save(
            UUID id, String name, String taxId, boolean active, UUID planId, Integer trialDays) {
        current.requireSuperAdmin();
        var previous =
                id == null ? null : businesses.find(id).orElseThrow(BusinessException::notFound);
        var now = clock.instant();
        var business =
                businesses.save(
                        new Business(
                                id == null ? UUID.randomUUID() : id,
                                name.trim(),
                                taxId,
                                active,
                                previous == null ? now : previous.createdAt(),
                                now));
        if (previous == null)
            settings.save(
                    new BusinessSettings(
                            business.id(),
                            "POS",
                            true,
                            true,
                            true,
                            new BigDecimal("0.19"),
                            true,
                            "America/Santiago"));
        audit.record(
                business.id(),
                current.userId(),
                previous == null ? "BUSINESS_CREATED" : "BUSINESS_UPDATED",
                "business",
                business.id());
        if (previous == null) {
            UUID selectedPlan =
                    planId != null
                            ? planId
                            : plans.findDefaultActive()
                                    .orElseThrow(() -> new BusinessException("PLAN_REQUIRED", 409))
                                    .id();
            subscriptionAdmin.assign(business.id(), selectedPlan, trialDays);
        } else if (planId != null) {
            var subscription = subscriptions.findByBusiness(business.id()).orElse(null);
            if (subscription == null) {
                subscriptionAdmin.assign(business.id(), planId, trialDays);
            } else if (!subscription.planId().equals(planId)) {
                subscriptionAdmin.changePlan(subscription.id(), planId);
            }
        }
        return view(business);
    }

    private BusinessView view(Business business) {
        var subscription = subscriptions.findByBusiness(business.id()).orElse(null);
        var plan = subscription == null ? null : plans.find(subscription.planId()).orElse(null);
        return new BusinessView(
                business.id(),
                business.name(),
                business.taxId(),
                business.active(),
                business.createdAt(),
                business.updatedAt(),
                plan == null ? null : plan.id(),
                plan == null ? null : plan.name(),
                subscription == null ? null : subscription.status(),
                subscription == null ? null : subscription.trialEndsAt(),
                subscription == null ? null : subscription.nextBillingDate());
    }
}
