package com.stockflow.realtime.infrastructure;

import com.stockflow.realtime.application.RealtimeDeliveryExecutor;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BoundedRealtimeDeliveryExecutor implements RealtimeDeliveryExecutor {
    private final ThreadPoolExecutor executor;

    public BoundedRealtimeDeliveryExecutor(
            @Value("${app.realtime.delivery-threads:4}") int threads,
            @Value("${app.realtime.delivery-queue-capacity:512}") int queueCapacity) {
        if (threads < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("Invalid realtime executor capacity");
        }
        executor =
                new ThreadPoolExecutor(
                        threads,
                        threads,
                        0,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(queueCapacity),
                        Thread.ofPlatform().name("stockflow-sse-", 0).factory(),
                        // Backpressure preserves control events if the bounded queue is saturated.
                        new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public void execute(Runnable delivery) {
        executor.execute(delivery);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
