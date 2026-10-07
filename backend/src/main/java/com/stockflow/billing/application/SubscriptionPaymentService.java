package com.stockflow.billing.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.billing.domain.*;
import com.stockflow.businesses.domain.BusinessRepository;
import com.stockflow.shared.application.AuditService;
import com.stockflow.shared.domain.BusinessException;
import com.stockflow.shared.domain.IdempotencyLock;
import com.stockflow.shared.domain.Page;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubscriptionPaymentService {
    private static final Set<String> MANUAL_METHODS =
            Set.of("MANUAL_TRANSFER", "MANUAL_CASH", "OTHER");

    private final SubscriptionPaymentRepository payments;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final BusinessRepository businesses;
    private final SubscriptionPaymentProvider provider;
    private final IdempotencyLock idempotency;
    private final CurrentIdentity current;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SubscriptionPaymentService(
            SubscriptionPaymentRepository payments,
            SubscriptionRepository subscriptions,
            SubscriptionPlanRepository plans,
            BusinessRepository businesses,
            SubscriptionPaymentProvider provider,
            IdempotencyLock idempotency,
            CurrentIdentity current,
            AuditService audit,
            ApplicationEventPublisher events,
            Clock clock) {
        this.payments = payments;
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.businesses = businesses;
        this.provider = provider;
        this.idempotency = idempotency;
        this.current = current;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionPaymentView> listAdmin(
            UUID businessId, PaymentStatus status, int page, int size) {
        current.requireSuperAdmin();
        return map(payments.list(businessId, status, page, size));
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionPaymentView> listForCurrentBusiness(
            PaymentStatus status, int page, int size) {
        current.requireManager();
        return map(payments.list(current.businessId(), status, page, size));
    }

    public SubscriptionPaymentView requestForCurrentBusiness(
            UUID requestId, String paymentMethod, String externalReference) {
        current.requireManager();
        UUID businessId = current.businessId();
        var subscription =
                subscriptions
                        .findByBusiness(businessId)
                        .orElseThrow(() -> new BusinessException("SUBSCRIPTION_REQUIRED", 403));
        var plan = plans.find(subscription.planId()).orElseThrow(BusinessException::notFound);
        return register(
                businessId, requestId, plan.monthlyPrice(), paymentMethod, externalReference);
    }

    public SubscriptionPaymentView registerAdmin(
            UUID businessId,
            UUID requestId,
            BigDecimal amount,
            String paymentMethod,
            String externalReference) {
        current.requireSuperAdmin();
        return register(businessId, requestId, amount, paymentMethod, externalReference);
    }

    public SubscriptionPaymentView confirm(UUID paymentId, String externalReference) {
        current.requireSuperAdmin();
        var stored = payments.lock(paymentId).orElseThrow(BusinessException::notFound);
        if (stored.status() == PaymentStatus.PAID) return view(stored);
        if (stored.status() != PaymentStatus.PENDING) {
            throw new BusinessException("PAYMENT_NOT_PENDING", 409);
        }
        var subscription =
                subscriptions
                        .lockByBusiness(stored.businessId())
                        .orElseThrow(BusinessException::notFound);
        if (!subscription.id().equals(stored.subscriptionId())) {
            throw new BusinessException("TENANT_MISMATCH", 409);
        }
        var plan = plans.find(subscription.planId()).orElseThrow(BusinessException::notFound);
        var now = clock.instant();
        var paid =
                payments.save(
                        new SubscriptionPayment(
                                stored.id(),
                                stored.subscriptionId(),
                                stored.businessId(),
                                stored.requestId(),
                                stored.amount(),
                                stored.currency(),
                                stored.paymentMethod(),
                                PaymentStatus.PAID,
                                normalizeReference(
                                        externalReference == null
                                                ? stored.externalReference()
                                                : externalReference),
                                now,
                                stored.createdAt(),
                                now));
        var activated =
                subscriptions.save(SubscriptionStateMachine.activatePaid(subscription, plan, now));
        audit.record(
                stored.businessId(),
                current.userId(),
                "SUBSCRIPTION_PAYMENT_CONFIRMED",
                "subscription_payment",
                paid.id());
        events.publishEvent(
                new SubscriptionChangedEvent(
                        activated.businessId(), activated.id(), activated.status().name()));
        return view(paid);
    }

    private SubscriptionPaymentView register(
            UUID businessId,
            UUID requestId,
            BigDecimal amount,
            String paymentMethod,
            String externalReference) {
        if (businessId == null || businesses.find(businessId).isEmpty()) {
            throw BusinessException.notFound();
        }
        if (requestId == null) requestId = UUID.randomUUID();
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new BusinessException("VALIDATION_ERROR", 400);
        }
        String method = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase();
        if (!MANUAL_METHODS.contains(method)) {
            throw new BusinessException("PAYMENT_METHOD_UNSUPPORTED", 400);
        }
        String reference = normalizeReference(externalReference);
        idempotency.acquire(businessId, requestId);
        var prior = payments.findByRequest(businessId, requestId);
        if (prior.isPresent()) {
            var payment = prior.orElseThrow();
            if (payment.amount().compareTo(amount) != 0
                    || !payment.paymentMethod().equals(method)
                    || !java.util.Objects.equals(payment.externalReference(), reference)) {
                throw new BusinessException("IDEMPOTENCY_CONFLICT", 409);
            }
            return view(payment);
        }
        var subscription =
                subscriptions
                        .lockByBusiness(businessId)
                        .orElseThrow(() -> new BusinessException("SUBSCRIPTION_REQUIRED", 409));
        if (subscription.status() == SubscriptionStatus.CANCELLED) {
            throw new BusinessException("SUBSCRIPTION_CANCELLED", 409);
        }
        var now = clock.instant();
        var payment =
                new SubscriptionPayment(
                        UUID.randomUUID(),
                        subscription.id(),
                        businessId,
                        requestId,
                        amount,
                        SubscriptionAdminService.CURRENCY,
                        method,
                        PaymentStatus.PENDING,
                        reference,
                        null,
                        now,
                        now);
        provider.validateRegistration(payment);
        var saved = payments.save(payment);
        audit.record(
                businessId,
                current.userId(),
                "SUBSCRIPTION_PAYMENT_REGISTERED",
                "subscription_payment",
                saved.id());
        return view(saved);
    }

    private Page<SubscriptionPaymentView> map(Page<SubscriptionPayment> source) {
        return new Page<>(
                source.items().stream().map(this::view).toList(),
                source.total(),
                source.page(),
                source.size());
    }

    private SubscriptionPaymentView view(SubscriptionPayment payment) {
        String businessName =
                businesses
                        .find(payment.businessId())
                        .orElseThrow(BusinessException::notFound)
                        .name();
        return new SubscriptionPaymentView(
                payment.id(),
                payment.subscriptionId(),
                payment.businessId(),
                businessName,
                payment.amount(),
                payment.currency(),
                payment.paymentMethod(),
                payment.status(),
                payment.externalReference(),
                payment.paidAt(),
                payment.createdAt(),
                payment.updatedAt());
    }

    private static String normalizeReference(String reference) {
        if (reference == null || reference.isBlank()) return null;
        String normalized = reference.trim();
        if (normalized.length() > 160) throw new BusinessException("VALIDATION_ERROR", 400);
        return normalized;
    }
}
