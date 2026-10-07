package com.stockflow.realtime.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.auth.domain.SessionIdentity;
import com.stockflow.realtime.application.RealtimeBroker;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RealtimeControllerTest {
    @Test
    void derivesBusinessAndUserOnlyFromTheCurrentIdentity() {
        var broker = mock(RealtimeBroker.class);
        var current = mock(CurrentIdentity.class);
        var response = mock(HttpServletResponse.class);
        var emitter = new SseEmitter();
        var businessId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        when(current.businessId()).thenReturn(businessId);
        when(current.get()).thenReturn(new SessionIdentity(userId, businessId, "BUSINESS_USER", 3));
        when(broker.subscribe(businessId, userId, 3, "BUSINESS_USER")).thenReturn(emitter);

        var result = new RealtimeController(broker, current).events(response);

        assertThat(result).isSameAs(emitter);
        verify(broker).subscribe(businessId, userId, 3, "BUSINESS_USER");
        verify(response).setHeader("Cache-Control", "no-store");
        verify(response).setHeader("X-Accel-Buffering", "no");
    }
}
