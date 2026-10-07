package com.stockflow.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stockflow.billing.domain.SubscriptionAccessPolicy;
import com.stockflow.businesses.domain.Business;
import com.stockflow.businesses.domain.BusinessRepository;
import com.stockflow.users.domain.Account;
import com.stockflow.users.domain.AccountRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RealtimeBrokerTest {
    @Test
    void publishesOnlyInsideTheAuthenticatedBusinessChannel() {
        var factory = new RecordingEmitterFactory();
        var accounts = mock(AccountRepository.class);
        var businesses = mock(BusinessRepository.class);
        var broker = broker(factory, accounts, businesses, permitted(), 60_000);
        var businessA = UUID.randomUUID();
        var businessB = UUID.randomUUID();
        var userA = UUID.randomUUID();
        var userB = UUID.randomUUID();
        var userC = UUID.randomUUID();
        authorize(accounts, userA, businessA);
        authorize(accounts, userB, businessA);
        authorize(accounts, userC, businessB);
        active(businesses, businessA);
        active(businesses, businessB);

        broker.subscribe(businessA, userA, 1, "BUSINESS_USER");
        broker.subscribe(businessA, userB, 1, "BUSINESS_USER");
        broker.subscribe(businessB, userC, 1, "BUSINESS_USER");
        broker.publish(businessA, RealtimeMessage.now("PRODUCT_STOCK_CHANGED", UUID.randomUUID()));

        assertThat(factory.emitters)
                .extracting(RecordingEmitter::sendCount)
                .containsExactly(2, 2, 1);
        assertThat(broker.connectionCount(businessA)).isEqualTo(2);
        assertThat(broker.connectionCount(businessB)).isOne();
    }

    @Test
    void removesCompletedTimedOutAndBrokenConnections() {
        var factory = new RecordingEmitterFactory();
        var accounts = mock(AccountRepository.class);
        var businesses = mock(BusinessRepository.class);
        var broker = broker(factory, accounts, businesses, permitted(), 60_000);
        var businessId = UUID.randomUUID();
        var firstUser = UUID.randomUUID();
        var secondUser = UUID.randomUUID();
        authorize(accounts, firstUser, businessId);
        authorize(accounts, secondUser, businessId);
        active(businesses, businessId);

        broker.subscribe(businessId, firstUser, 1, "BUSINESS_USER");
        broker.subscribe(businessId, secondUser, 1, "BUSINESS_USER");
        factory.emitters.getFirst().finish();
        assertThat(broker.connectionCount(businessId)).isOne();

        factory.emitters.get(1).timeOut();
        assertThat(broker.connectionCount(businessId)).isZero();

        broker.subscribe(businessId, secondUser, 1, "BUSINESS_USER");
        factory.emitters.get(2).failNextSend = true;
        broker.heartbeat();
        assertThat(broker.connectionCount(businessId)).isZero();
        assertThat(factory.emitters.get(2).completedWithError).isTrue();
    }

    @Test
    void canCloseOneUsersStreamsOrAnEntireBusiness() {
        var factory = new RecordingEmitterFactory();
        var broker =
                new RealtimeBroker(
                        factory,
                        Runnable::run,
                        mock(AccountRepository.class),
                        mock(BusinessRepository.class),
                        permitted(),
                        60_000);
        var businessId = UUID.randomUUID();
        var firstUser = UUID.randomUUID();
        var secondUser = UUID.randomUUID();

        broker.subscribe(businessId, firstUser, 1, "BUSINESS_USER");
        broker.subscribe(businessId, firstUser, 1, "BUSINESS_USER");
        broker.subscribe(businessId, secondUser, 1, "BUSINESS_USER");
        broker.closeUser(businessId, firstUser);

        assertThat(broker.connectionCount(businessId)).isOne();
        assertThat(factory.emitters)
                .extracting(emitter -> emitter.completed)
                .containsExactly(true, true, false);

        broker.closeBusiness(businessId);
        assertThat(broker.connectionCount(businessId)).isZero();
        assertThat(factory.emitters.get(2).completed).isTrue();
    }

    @Test
    void disconnectsAnIdentityThatWasRevokedAfterOpeningTheStream() {
        var factory = new RecordingEmitterFactory();
        var accounts = mock(AccountRepository.class);
        var businesses = mock(BusinessRepository.class);
        var broker = broker(factory, accounts, businesses, permitted(), 60_000);
        var businessId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        active(businesses, businessId);
        authorize(accounts, userId, businessId);
        broker.subscribe(businessId, userId, 1, "BUSINESS_USER");

        when(accounts.find(userId)).thenReturn(Optional.empty());
        broker.publish(businessId, RealtimeMessage.now("SALE_CONFIRMED", UUID.randomUUID()));

        assertThat(broker.connectionCount(businessId)).isZero();
        assertThat(factory.emitters.getFirst().completed).isTrue();
        assertThat(factory.emitters.getFirst().sendCount()).isOne();
    }

    @Test
    void suppressesCommercialEventsWithoutAccessButStillDeliversSubscriptionChanges() {
        var factory = new RecordingEmitterFactory();
        var accounts = mock(AccountRepository.class);
        var businesses = mock(BusinessRepository.class);
        var subscriptions = mock(SubscriptionAccessPolicy.class);
        var businessId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        active(businesses, businessId);
        authorize(accounts, userId, businessId);
        when(subscriptions.hasCommercialAccess(businessId)).thenReturn(false);
        var broker = broker(factory, accounts, businesses, subscriptions, 60_000);
        broker.subscribe(businessId, userId, 1, "BUSINESS_USER");

        broker.publish(businessId, RealtimeMessage.now("PRODUCT_STOCK_CHANGED", UUID.randomUUID()));
        broker.publish(businessId, RealtimeMessage.now("SUBSCRIPTION_CHANGED", UUID.randomUUID()));

        assertThat(factory.emitters.getFirst().sendCount()).isEqualTo(2);
        assertThat(broker.connectionCount(businessId)).isOne();
    }

    @Test
    void heartbeatRevokesAConnectionWhoseTenantChangesWhileItIsOpen() {
        var factory = new RecordingEmitterFactory();
        var accounts = mock(AccountRepository.class);
        var businesses = mock(BusinessRepository.class);
        var businessId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        active(businesses, businessId);
        authorize(accounts, userId, businessId);
        var broker = broker(factory, accounts, businesses, permitted(), 60_000);
        broker.subscribe(businessId, userId, 1, "BUSINESS_USER");

        authorize(accounts, userId, UUID.randomUUID());
        broker.heartbeat();

        assertThat(broker.connectionCount(businessId)).isZero();
        assertThat(factory.emitters.getFirst().completed).isTrue();
    }

    private static RealtimeBroker broker(
            RecordingEmitterFactory factory,
            AccountRepository accounts,
            BusinessRepository businesses,
            SubscriptionAccessPolicy subscriptions,
            long timeoutMillis) {
        return new RealtimeBroker(
                factory, Runnable::run, accounts, businesses, subscriptions, timeoutMillis);
    }

    private static SubscriptionAccessPolicy permitted() {
        var subscriptions = mock(SubscriptionAccessPolicy.class);
        when(subscriptions.hasCommercialAccess(org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        return subscriptions;
    }

    private static void active(BusinessRepository businesses, UUID businessId) {
        var business = mock(Business.class);
        when(business.active()).thenReturn(true);
        when(businesses.find(businessId)).thenReturn(Optional.of(business));
    }

    private static void authorize(AccountRepository accounts, UUID userId, UUID businessId) {
        var account = mock(Account.class);
        when(account.active()).thenReturn(true);
        when(account.mustChangePassword()).thenReturn(false);
        when(account.businessId()).thenReturn(businessId);
        when(account.accessVersion()).thenReturn(1);
        when(account.role()).thenReturn("BUSINESS_USER");
        when(accounts.find(userId)).thenReturn(Optional.of(account));
    }

    private static final class RecordingEmitterFactory implements RealtimeEmitterFactory {
        private final List<RecordingEmitter> emitters = new ArrayList<>();

        @Override
        public SseEmitter create(long timeoutMillis) {
            var emitter = new RecordingEmitter(timeoutMillis);
            emitters.add(emitter);
            return emitter;
        }
    }

    private static final class RecordingEmitter extends SseEmitter {
        private int sendCount;
        private boolean failNextSend;
        private boolean completed;
        private boolean completedWithError;
        private Runnable completion;
        private Runnable timeout;
        private Consumer<Throwable> error;

        private RecordingEmitter(long timeoutMillis) {
            super(timeoutMillis);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failNextSend) throw new IOException("disconnected");
            sendCount++;
        }

        @Override
        public void onCompletion(Runnable callback) {
            completion = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeout = callback;
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            error = callback;
        }

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void completeWithError(Throwable failure) {
            completedWithError = true;
        }

        private int sendCount() {
            return sendCount;
        }

        private void finish() {
            completion.run();
        }

        private void timeOut() {
            timeout.run();
        }
    }
}
