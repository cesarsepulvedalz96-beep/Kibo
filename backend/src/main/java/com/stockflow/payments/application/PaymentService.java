package com.stockflow.payments.application;

import com.stockflow.payments.domain.PaymentStrategy;
import com.stockflow.shared.domain.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private final List<PaymentStrategy> strategies;

    public PaymentService(List<PaymentStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public PaymentStrategy.PaymentResult record(
            BigDecimal total, PaymentStrategy.PaymentRequest request) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(request.method()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("INVALID_PAYMENT", 400))
                .record(total, request);
    }
}
