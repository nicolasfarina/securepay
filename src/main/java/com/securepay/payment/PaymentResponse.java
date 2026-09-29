package com.securepay.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String customerId,
        String merchantId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt) {

    static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.id(), payment.customerId(), payment.merchantId(), payment.amount(),
                payment.currency(), payment.status(), payment.createdAt(), payment.updatedAt());
    }
}
