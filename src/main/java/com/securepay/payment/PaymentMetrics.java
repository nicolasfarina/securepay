package com.securepay.payment;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {
    static final String TRANSITIONS = "securepay.payment.status.transitions";

    private final MeterRegistry registry;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordTransition(PaymentStatus from, PaymentStatus to) {
        Runnable increment = () -> registry.counter(TRANSITIONS,
                "from", from.name(), "to", to.name()).increment();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    increment.run();
                }
            });
        } else {
            increment.run();
        }
    }
}
