package com.stockflow.realtime.infrastructure;

import com.stockflow.realtime.application.RealtimeEmitterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SpringRealtimeEmitterFactory implements RealtimeEmitterFactory {
    @Override
    public SseEmitter create(long timeoutMillis) {
        return new SseEmitter(timeoutMillis);
    }
}
