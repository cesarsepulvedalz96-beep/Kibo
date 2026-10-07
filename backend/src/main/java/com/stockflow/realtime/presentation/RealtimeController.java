package com.stockflow.realtime.presentation;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.realtime.application.RealtimeBroker;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/events")
public class RealtimeController {
    private final RealtimeBroker broker;
    private final CurrentIdentity current;

    public RealtimeController(RealtimeBroker broker, CurrentIdentity current) {
        this.broker = broker;
        this.current = current;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Accel-Buffering", "no");
        var businessId = current.businessId();
        var identity = current.get();
        return broker.subscribe(
                businessId, identity.userId(), identity.accessVersion(), identity.role());
    }
}
