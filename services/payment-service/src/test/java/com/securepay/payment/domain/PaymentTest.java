package com.securepay.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    void createsAValidPaymentWithPendingStatus() {
        Payment payment = Payment.create(new BigDecimal("125.50"), "USD", "payment-request-123");

        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getAmount()).isEqualByComparingTo("125.50");
        assertThat(payment.getCurrency()).isEqualTo("USD");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getIdempotencyKey()).isEqualTo("payment-request-123");
        assertThat(payment.getCreatedAt()).isNotNull();
    }
}
