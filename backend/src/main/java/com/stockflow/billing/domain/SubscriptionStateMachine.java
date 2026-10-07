package com.stockflow.billing.domain;

import com.stockflow.shared.domain.BusinessException;
import java.time.Instant;

/** The only place that interprets subscription dates and automatic transitions. */
public final class SubscriptionStateMachine {
    private SubscriptionStateMachine() {}

    public static Subscription advance(
            Subscription subscription, SubscriptionPlan plan, Instant now) {
        return switch (subscription.status()) {
            case TRIAL ->
                    due(subscription.trialEndsAt(), now)
                            ? withStatus(
                                    subscription,
                                    SubscriptionStatus.SUSPENDED,
                                    null,
                                    null,
                                    subscription.trialEndsAt(),
                                    now,
                                    now)
                            : subscription;
            case ACTIVE -> {
                if (!due(subscription.currentPeriodEnd(), now)) yield subscription;
                Instant graceEnd =
                        subscription
                                .currentPeriodEnd()
                                .plusSeconds((long) plan.gracePeriodDays() * 86_400);
                yield plan.gracePeriodDays() == 0 || due(graceEnd, now)
                        ? withStatus(
                                subscription,
                                SubscriptionStatus.SUSPENDED,
                                null,
                                null,
                                null,
                                now,
                                now)
                        : withStatus(
                                subscription,
                                SubscriptionStatus.GRACE_PERIOD,
                                graceEnd,
                                subscription.nextBillingDate(),
                                null,
                                null,
                                now);
            }
            case GRACE_PERIOD ->
                    due(subscription.graceEndsAt(), now)
                            ? withStatus(
                                    subscription,
                                    SubscriptionStatus.SUSPENDED,
                                    subscription.graceEndsAt(),
                                    subscription.nextBillingDate(),
                                    null,
                                    now,
                                    now)
                            : subscription;
            case SUSPENDED, CANCELLED -> subscription;
        };
    }

    public static Subscription activatePaid(
            Subscription subscription, SubscriptionPlan plan, Instant now) {
        if (subscription.status() == SubscriptionStatus.CANCELLED) {
            throw new BusinessException("SUBSCRIPTION_CANCELLED", 409);
        }
        Instant start =
                subscription.status() == SubscriptionStatus.ACTIVE
                                && subscription.currentPeriodEnd() != null
                                && subscription.currentPeriodEnd().isAfter(now)
                        ? subscription.currentPeriodEnd()
                        : now;
        Instant end = start.plusSeconds((long) plan.billingPeriodDays() * 86_400);
        return new Subscription(
                subscription.id(),
                subscription.businessId(),
                subscription.planId(),
                SubscriptionStatus.ACTIVE,
                subscription.startedAt(),
                null,
                start,
                end,
                null,
                end,
                null,
                subscription.createdAt(),
                now);
    }

    private static boolean due(Instant deadline, Instant now) {
        return deadline != null && !deadline.isAfter(now);
    }

    private static Subscription withStatus(
            Subscription s,
            SubscriptionStatus status,
            Instant graceEndsAt,
            Instant nextBillingDate,
            Instant trialEndsAt,
            Instant suspendedAt,
            Instant updatedAt) {
        return new Subscription(
                s.id(),
                s.businessId(),
                s.planId(),
                status,
                s.startedAt(),
                trialEndsAt,
                s.currentPeriodStart(),
                s.currentPeriodEnd(),
                graceEndsAt,
                nextBillingDate,
                suspendedAt,
                s.createdAt(),
                updatedAt);
    }
}
