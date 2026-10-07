package com.stockflow.billing.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionStateMachineTest {
    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final SubscriptionPlan PLAN =
            new SubscriptionPlan(
                    PLAN_ID, "Standard", null, new BigDecimal("120000"), 30, 7, true, NOW, NOW);

    @Test
    void expiredTrialSuspendsAndRepeatedAdvanceIsIdempotent() {
        var trial = subscription(SubscriptionStatus.TRIAL, NOW.minusSeconds(1), null, null);
        var suspended = SubscriptionStateMachine.advance(trial, PLAN, NOW);

        assertThat(suspended.status()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(suspended.suspendedAt()).isEqualTo(NOW);
        assertThat(SubscriptionStateMachine.advance(suspended, PLAN, NOW)).isSameAs(suspended);
    }

    @Test
    void expiredActiveEntersGraceAndExpiredGraceSuspends() {
        var active = subscription(SubscriptionStatus.ACTIVE, null, NOW.minusSeconds(1), null);
        var grace = SubscriptionStateMachine.advance(active, PLAN, NOW);

        assertThat(grace.status()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
        assertThat(grace.graceEndsAt()).isEqualTo(NOW.minusSeconds(1).plusSeconds(7 * 86_400L));
        var expiredGrace =
                new Subscription(
                        grace.id(),
                        grace.businessId(),
                        grace.planId(),
                        grace.status(),
                        grace.startedAt(),
                        grace.trialEndsAt(),
                        grace.currentPeriodStart(),
                        grace.currentPeriodEnd(),
                        NOW.minusSeconds(1),
                        grace.nextBillingDate(),
                        null,
                        grace.createdAt(),
                        grace.updatedAt());
        assertThat(SubscriptionStateMachine.advance(expiredGrace, PLAN, NOW).status())
                .isEqualTo(SubscriptionStatus.SUSPENDED);
    }

    @Test
    void paidSuspendedSubscriptionStartsANewPeriodAndCancelledCannotReactivate() {
        var suspended =
                subscription(SubscriptionStatus.SUSPENDED, null, null, NOW.minusSeconds(10));
        var active = SubscriptionStateMachine.activatePaid(suspended, PLAN, NOW);

        assertThat(active.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(active.currentPeriodStart()).isEqualTo(NOW);
        assertThat(active.currentPeriodEnd()).isEqualTo(NOW.plusSeconds(30 * 86_400L));

        var cancelled =
                subscription(SubscriptionStatus.CANCELLED, null, null, NOW.minusSeconds(10));
        assertThatThrownBy(() -> SubscriptionStateMachine.activatePaid(cancelled, PLAN, NOW))
                .hasMessage("SUBSCRIPTION_CANCELLED");
    }

    private static Subscription subscription(
            SubscriptionStatus status,
            Instant trialEndsAt,
            Instant currentPeriodEnd,
            Instant suspendedAt) {
        return new Subscription(
                UUID.randomUUID(),
                BUSINESS_ID,
                PLAN_ID,
                status,
                NOW.minusSeconds(86_400),
                trialEndsAt,
                currentPeriodEnd == null ? null : NOW.minusSeconds(86_400),
                currentPeriodEnd,
                null,
                currentPeriodEnd,
                suspendedAt,
                NOW.minusSeconds(86_400),
                NOW.minusSeconds(60));
    }
}
