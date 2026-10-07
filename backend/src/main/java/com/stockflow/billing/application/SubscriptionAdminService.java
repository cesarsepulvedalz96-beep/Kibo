package com.stockflow.billing.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.billing.domain.*;
import com.stockflow.businesses.domain.BusinessRepository;
import com.stockflow.shared.application.AuditService;
import com.stockflow.shared.domain.BusinessException;
import com.stockflow.shared.domain.Page;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubscriptionAdminService {
    public static final String CURRENCY = "CLP";

    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final BusinessRepository businesses;
    private final CurrentIdentity current;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SubscriptionAdminService(
            SubscriptionRepository subscriptions,
            SubscriptionPlanRepository plans,
            BusinessRepository businesses,
            CurrentIdentity current,
            AuditService audit,
            ApplicationEventPublisher events,
            Clock clock) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.businesses = businesses;
        this.current = current;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionView> list(
            UUID businessId, SubscriptionStatus status, int page, int size) {
        current.requireSuperAdmin();
        var result = subscriptions.list(businessId, status, page, size);
        return new Page<>(
                result.items().stream().map(this::view).toList(), result.total(), page, size);
    }

    @Transactional(readOnly = true)
    public SubscriptionView getForBusiness(UUID businessId) {
        return view(
                subscriptions
                        .findByBusiness(businessId)
                        .orElseThrow(() -> new BusinessException("SUBSCRIPTION_REQUIRED", 403)));
    }

    public SubscriptionView assign(UUID businessId, UUID planId, Integer trialDays) {
        current.requireSuperAdmin();
        if (businesses.find(businessId).isEmpty()) throw BusinessException.notFound();
        if (subscriptions.findByBusiness(businessId).isPresent()) {
            throw new BusinessException("SUBSCRIPTION_ALREADY_EXISTS", 409);
        }
        var plan = activePlan(planId);
        int trial = trialDays == null ? 0 : trialDays;
        if (trial > 0) validateTrialDays(trial);
        var now = clock.instant();
        var status = trial > 0 ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE;
        var end =
                status == SubscriptionStatus.ACTIVE
                        ? plusDays(now, plan.billingPeriodDays())
                        : null;
        var saved =
                subscriptions.save(
                        new Subscription(
                                UUID.randomUUID(),
                                businessId,
                                plan.id(),
                                status,
                                now,
                                trial > 0 ? plusDays(now, trial) : null,
                                status == SubscriptionStatus.ACTIVE ? now : null,
                                end,
                                null,
                                end,
                                null,
                                now,
                                now));
        changed(saved, trial > 0 ? "SUBSCRIPTION_TRIAL_STARTED" : "SUBSCRIPTION_ACTIVATED");
        return view(saved);
    }

    public SubscriptionView startTrial(UUID id, int trialDays) {
        current.requireSuperAdmin();
        validateTrialDays(trialDays);
        var stored = lock(id);
        if (stored.status() == SubscriptionStatus.CANCELLED) conflict("SUBSCRIPTION_CANCELLED");
        var now = clock.instant();
        var saved =
                subscriptions.save(
                        copy(
                                stored,
                                stored.planId(),
                                SubscriptionStatus.TRIAL,
                                plusDays(now, trialDays),
                                null,
                                null,
                                null,
                                null,
                                null,
                                now));
        changed(saved, "SUBSCRIPTION_TRIAL_STARTED");
        return view(saved);
    }

    public SubscriptionView extendTrial(UUID id, int trialDays) {
        current.requireSuperAdmin();
        validateTrialDays(trialDays);
        var stored = lock(id);
        if (stored.status() != SubscriptionStatus.TRIAL
                && !(stored.status() == SubscriptionStatus.SUSPENDED
                        && stored.trialEndsAt() != null)) {
            conflict("SUBSCRIPTION_NOT_TRIAL");
        }
        var now = clock.instant();
        var base = stored.trialEndsAt().isAfter(now) ? stored.trialEndsAt() : now;
        var saved =
                subscriptions.save(
                        copy(
                                stored,
                                stored.planId(),
                                SubscriptionStatus.TRIAL,
                                plusDays(base, trialDays),
                                null,
                                null,
                                null,
                                null,
                                null,
                                now));
        changed(saved, "SUBSCRIPTION_TRIAL_EXTENDED");
        return view(saved);
    }

    public SubscriptionView changePlan(UUID id, UUID planId) {
        current.requireSuperAdmin();
        var plan = activePlan(planId);
        var stored = lock(id);
        if (stored.status() == SubscriptionStatus.CANCELLED) conflict("SUBSCRIPTION_CANCELLED");
        var saved =
                subscriptions.save(
                        copy(
                                stored,
                                plan.id(),
                                stored.status(),
                                stored.trialEndsAt(),
                                stored.currentPeriodStart(),
                                stored.currentPeriodEnd(),
                                stored.graceEndsAt(),
                                stored.nextBillingDate(),
                                stored.suspendedAt(),
                                clock.instant()));
        changed(saved, "SUBSCRIPTION_PLAN_CHANGED");
        return view(saved);
    }

    public SubscriptionView suspend(UUID id) {
        return setStatus(id, SubscriptionStatus.SUSPENDED, "SUBSCRIPTION_SUSPENDED");
    }

    public SubscriptionView reactivate(UUID id) {
        current.requireSuperAdmin();
        var stored = lock(id);
        if (stored.status() == SubscriptionStatus.CANCELLED) conflict("SUBSCRIPTION_CANCELLED");
        if (stored.status() == SubscriptionStatus.ACTIVE) return view(stored);
        var plan = plans.find(stored.planId()).orElseThrow(BusinessException::notFound);
        var saved =
                subscriptions.save(
                        SubscriptionStateMachine.activatePaid(stored, plan, clock.instant()));
        changed(saved, "SUBSCRIPTION_REACTIVATED");
        return view(saved);
    }

    public SubscriptionView activate(UUID id) {
        return reactivate(id);
    }

    public SubscriptionView cancel(UUID id) {
        return setStatus(id, SubscriptionStatus.CANCELLED, "SUBSCRIPTION_CANCELLED");
    }

    public SubscriptionView view(Subscription subscription) {
        var business =
                businesses.find(subscription.businessId()).orElseThrow(BusinessException::notFound);
        var plan = plans.find(subscription.planId()).orElseThrow(BusinessException::notFound);
        return new SubscriptionView(
                subscription.id(),
                subscription.businessId(),
                business.name(),
                plan.id(),
                plan.name(),
                plan.monthlyPrice(),
                CURRENCY,
                subscription.status(),
                subscription.startedAt(),
                subscription.trialEndsAt(),
                subscription.currentPeriodStart(),
                subscription.currentPeriodEnd(),
                subscription.graceEndsAt(),
                subscription.nextBillingDate(),
                subscription.suspendedAt(),
                remainingDays(subscription, clock.instant()),
                subscription.createdAt(),
                subscription.updatedAt());
    }

    private SubscriptionView setStatus(UUID id, SubscriptionStatus status, String action) {
        current.requireSuperAdmin();
        var stored = lock(id);
        if (stored.status() == status) return view(stored);
        if (stored.status() == SubscriptionStatus.CANCELLED) conflict("SUBSCRIPTION_CANCELLED");
        var now = clock.instant();
        var saved =
                subscriptions.save(
                        copy(
                                stored,
                                stored.planId(),
                                status,
                                stored.trialEndsAt(),
                                stored.currentPeriodStart(),
                                stored.currentPeriodEnd(),
                                status == SubscriptionStatus.SUSPENDED
                                        ? stored.graceEndsAt()
                                        : null,
                                stored.nextBillingDate(),
                                status == SubscriptionStatus.SUSPENDED ? now : null,
                                now));
        changed(saved, action);
        return view(saved);
    }

    private Subscription lock(UUID id) {
        var found = subscriptions.find(id).orElseThrow(BusinessException::notFound);
        return subscriptions
                .lockByBusiness(found.businessId())
                .orElseThrow(BusinessException::notFound);
    }

    private SubscriptionPlan activePlan(UUID id) {
        var plan = plans.find(id).orElseThrow(BusinessException::notFound);
        if (!plan.active()) throw new BusinessException("PLAN_INACTIVE", 409);
        return plan;
    }

    private void changed(Subscription subscription, String action) {
        audit.record(
                subscription.businessId(),
                current.userId(),
                action,
                "subscription",
                subscription.id());
        events.publishEvent(
                new SubscriptionChangedEvent(
                        subscription.businessId(),
                        subscription.id(),
                        subscription.status().name()));
    }

    private static Subscription copy(
            Subscription s,
            UUID planId,
            SubscriptionStatus status,
            Instant trialEndsAt,
            Instant periodStart,
            Instant periodEnd,
            Instant graceEndsAt,
            Instant nextBillingDate,
            Instant suspendedAt,
            Instant updatedAt) {
        return new Subscription(
                s.id(),
                s.businessId(),
                planId,
                status,
                s.startedAt(),
                trialEndsAt,
                periodStart,
                periodEnd,
                graceEndsAt,
                nextBillingDate,
                suspendedAt,
                s.createdAt(),
                updatedAt);
    }

    private static Long remainingDays(Subscription s, Instant now) {
        Instant deadline =
                switch (s.status()) {
                    case TRIAL -> s.trialEndsAt();
                    case ACTIVE -> s.currentPeriodEnd();
                    case GRACE_PERIOD -> s.graceEndsAt();
                    case SUSPENDED, CANCELLED -> null;
                };
        if (deadline == null) return null;
        long seconds = Math.max(0, Duration.between(now, deadline).getSeconds());
        return (seconds + 86_399) / 86_400;
    }

    private static Instant plusDays(Instant value, int days) {
        return value.plusSeconds((long) days * 86_400);
    }

    private static void validateTrialDays(int days) {
        if (days < 1 || days > 3650) throw new BusinessException("VALIDATION_ERROR", 400);
    }

    private static void conflict(String code) {
        throw new BusinessException(code, 409);
    }
}
