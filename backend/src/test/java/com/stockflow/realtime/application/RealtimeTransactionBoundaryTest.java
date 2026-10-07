package com.stockflow.realtime.application;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.stockflow.sales.domain.SaleConfirmedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class RealtimeTransactionBoundaryTest {
    private AnnotationConfigApplicationContext context;
    private RealtimeBroker broker;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        broker = context.getBean(RealtimeBroker.class);
        transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void publishesOnlyAfterTheSurroundingTransactionCommits() {
        var event = saleEvent();

        transactions.executeWithoutResult(
                status -> {
                    context.publishEvent(event);
                    verify(broker, never())
                            .publish(
                                    org.mockito.ArgumentMatchers.any(),
                                    org.mockito.ArgumentMatchers.any());
                });

        verify(broker)
                .publish(
                        org.mockito.ArgumentMatchers.eq(event.businessId()),
                        org.mockito.ArgumentMatchers.argThat(
                                message ->
                                        message.type().equals("SALE_CONFIRMED")
                                                && message.resourceId().equals(event.saleId())));
    }

    @Test
    void doesNotPublishWhenTheSurroundingTransactionRollsBack() {
        var event = saleEvent();
        clearInvocations(broker);

        transactions.executeWithoutResult(
                status -> {
                    context.publishEvent(event);
                    status.setRollbackOnly();
                });

        verify(broker, never())
                .publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private SaleConfirmedEvent saleEvent() {
        return new SaleConfirmedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(RealtimeEventBridge.class)
    static class TestConfiguration {
        @Bean
        RealtimeBroker realtimeBroker() {
            return mock(RealtimeBroker.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
