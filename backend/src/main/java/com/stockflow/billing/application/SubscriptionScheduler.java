package com.stockflow.billing.application;

import com.stockflow.billing.domain.SubscriptionRepository;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionScheduler {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionLifecycleService lifecycle;
    private final Clock clock;

    public SubscriptionScheduler(
            SubscriptionRepository subscriptions,
            SubscriptionLifecycleService lifecycle,
            Clock clock) {
        this.subscriptions = subscriptions;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.billing.scheduler-cron:0 15 2 * * *}", zone = "UTC")
    public void advanceDueSubscriptions() {
        int batches = 0;
        while (batches++ < 1000) {
            var due = subscriptions.findBusinessesDue(clock.instant(), 1000);
            if (due.isEmpty()) return;
            due.forEach(lifecycle::refresh);
            if (due.size() < 1000) return;
        }
        throw new IllegalStateException("Subscription scheduler exceeded its safety batch limit");
    }
}
