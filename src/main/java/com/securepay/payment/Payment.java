package com.securepay.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        String customerId,
        String merchantId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public Payment transitionTo(PaymentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Cannot transition payment from " + status + " to " + target);
        }
        return new Payment(id, customerId, merchantId, amount, currency, target, createdAt, Instant.now());
    }
}
