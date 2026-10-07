package com.stockflow.billing.application;

import com.stockflow.billing.domain.*;
import com.stockflow.shared.application.AuditService;
import com.stockflow.shared.domain.BusinessException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionLifecycleService implements SubscriptionAccessPolicy {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SubscriptionLifecycleService(
            SubscriptionRepository subscriptions,
            SubscriptionPlanRepository plans,
            AuditService audit,
            ApplicationEventPublisher events,
            Clock clock) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Subscription refresh(UUID businessId) {
        var current =
                subscriptions
                        .lockByBusiness(businessId)
                        .orElseThrow(() -> new BusinessException("SUBSCRIPTION_REQUIRED", 403));
        var plan = plans.find(current.planId()).orElseThrow(BusinessException::notFound);
        var advanced = SubscriptionStateMachine.advance(current, plan, clock.instant());
        if (advanced.status() != current.status()) {
            advanced = subscriptions.save(advanced);
            audit.recordSystem(
                    businessId,
                    "SUBSCRIPTION_" + advanced.status().name(),
                    "subscription",
                    advanced.id());
            publish(advanced);
        }
        return advanced;
    }

    @Override
    @Transactional
    public boolean hasCommercialAccess(UUID businessId) {
        try {
            return refresh(businessId).status().permitsCommercialAccess();
        } catch (BusinessException exception) {
            if ("SUBSCRIPTION_REQUIRED".equals(exception.getMessage())) return false;
            throw exception;
        }
    }

    @Override
    @Transactional
    public void requireCommercialAccess(UUID businessId) {
        var subscription = refresh(businessId);
        if (!subscription.status().permitsCommercialAccess()) {
            throw new BusinessException(
                    subscription.status() == SubscriptionStatus.SUSPENDED
                            ? "SUBSCRIPTION_SUSPENDED"
                            : "SUBSCRIPTION_REQUIRED",
                    403);
        }
    }

    public void publish(Subscription subscription) {
        events.publishEvent(
                new SubscriptionChangedEvent(
                        subscription.businessId(),
                        subscription.id(),
                        subscription.status().name()));
    }
}
