package com.stockflow.realtime.application;

import com.stockflow.billing.domain.SubscriptionAccessPolicy;
import com.stockflow.businesses.domain.BusinessRepository;
import com.stockflow.users.domain.AccountRepository;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RealtimeBroker {
    private static final long RECONNECT_MILLIS = 3_000;

    private final RealtimeEmitterFactory emitters;
    private final RealtimeDeliveryExecutor deliveries;
    private final AccountRepository accounts;
    private final BusinessRepository businessRepository;
    private final SubscriptionAccessPolicy subscriptionAccess;
    private final long timeoutMillis;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Connection>> businesses =
            new ConcurrentHashMap<>();

    public RealtimeBroker(
            RealtimeEmitterFactory emitters,
            RealtimeDeliveryExecutor deliveries,
            AccountRepository accounts,
            BusinessRepository businessRepository,
            SubscriptionAccessPolicy subscriptionAccess,
            @Value("${app.realtime.timeout-ms:1800000}") long timeoutMillis) {
        this.emitters = emitters;
        this.deliveries = deliveries;
        this.accounts = accounts;
        this.businessRepository = businessRepository;
        this.subscriptionAccess = subscriptionAccess;
        this.timeoutMillis = timeoutMillis;
    }

    public SseEmitter subscribe(UUID businessId, UUID userId, int accessVersion, String role) {
        var connectionId = UUID.randomUUID();
        var emitter = emitters.create(timeoutMillis);
        var connection = new Connection(connectionId, userId, accessVersion, role, emitter);
        businesses
                .computeIfAbsent(businessId, ignored -> new ConcurrentHashMap<>())
                .put(connectionId, connection);

        emitter.onCompletion(() -> remove(businessId, connectionId));
        emitter.onTimeout(
                () -> {
                    remove(businessId, connectionId);
                    emitter.complete();
                });
        emitter.onError(error -> remove(businessId, connectionId));

        try {
            emitter.send(SseEmitter.event().comment("connected").reconnectTime(RECONNECT_MILLIS));
        } catch (IOException | IllegalStateException exception) {
            remove(businessId, connectionId);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publish(UUID businessId, RealtimeMessage message) {
        var connections = businesses.get(businessId);
        if (connections == null) return;
        connections.values().forEach(connection -> deliver(businessId, connection, message));
    }

    public void closeBusiness(UUID businessId) {
        var connections = businesses.remove(businessId);
        if (connections != null) {
            connections.values().forEach(connection -> connection.emitter().complete());
        }
    }

    public void closeUser(UUID businessId, UUID userId) {
        var connections = businesses.get(businessId);
        if (connections == null) return;
        connections.values().stream()
                .filter(connection -> connection.userId().equals(userId))
                .toList()
                .forEach(
                        connection -> {
                            remove(businessId, connection.id());
                            connection.emitter().complete();
                        });
    }

    @Scheduled(fixedDelayString = "${app.realtime.heartbeat-ms:25000}")
    void heartbeat() {
        businesses.forEach(
                (businessId, connections) ->
                        connections
                                .values()
                                .forEach(
                                        connection -> {
                                            deliveries.execute(
                                                    () ->
                                                            send(
                                                                    businessId,
                                                                    connection,
                                                                    SseEmitter.event()
                                                                            .comment("heartbeat")));
                                        }));
    }

    int connectionCount(UUID businessId) {
        var connections = businesses.get(businessId);
        return connections == null ? 0 : connections.size();
    }

    private void remove(UUID businessId, UUID connectionId) {
        businesses.computeIfPresent(
                businessId,
                (ignored, connections) -> {
                    connections.remove(connectionId);
                    return connections.isEmpty() ? null : connections;
                });
    }

    private void deliver(UUID businessId, Connection connection, RealtimeMessage message) {
        deliveries.execute(
                () -> {
                    if (!message.isSubscriptionControl()
                            && !subscriptionAccess.hasCommercialAccess(businessId)) {
                        return;
                    }
                    send(
                            businessId,
                            connection,
                            SseEmitter.event()
                                    .id(UUID.randomUUID().toString())
                                    .name(message.type())
                                    .data(message));
                });
    }

    private void send(UUID businessId, Connection connection, SseEmitter.SseEventBuilder event) {
        if (!isAuthorized(businessId, connection)) {
            remove(businessId, connection.id());
            connection.emitter().complete();
            return;
        }
        try {
            connection.emitter().send(event);
        } catch (IOException | IllegalStateException exception) {
            remove(businessId, connection.id());
            connection.emitter().completeWithError(exception);
        }
    }

    private boolean isAuthorized(UUID businessId, Connection connection) {
        boolean activeBusiness =
                businessRepository
                        .find(businessId)
                        .filter(business -> business.active())
                        .isPresent();
        if (!activeBusiness) return false;
        return accounts.find(connection.userId())
                .filter(account -> account.active() && !account.mustChangePassword())
                .filter(account -> Objects.equals(account.businessId(), businessId))
                .filter(account -> account.accessVersion() == connection.accessVersion())
                .filter(account -> account.role().equals(connection.role()))
                .isPresent();
    }

    private record Connection(
            UUID id, UUID userId, int accessVersion, String role, SseEmitter emitter) {}
}
