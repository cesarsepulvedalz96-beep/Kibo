package com.stockflow.realtime.application;

@FunctionalInterface
public interface RealtimeDeliveryExecutor {
    void execute(Runnable delivery);
}
