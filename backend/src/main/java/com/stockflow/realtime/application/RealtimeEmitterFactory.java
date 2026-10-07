package com.stockflow.realtime.application;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@FunctionalInterface
public interface RealtimeEmitterFactory {
    SseEmitter create(long timeoutMillis);
}
