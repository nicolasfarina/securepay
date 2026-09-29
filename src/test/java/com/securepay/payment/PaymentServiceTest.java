package com.securepay.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @BeforeEach
    void clearDatabase() {
        idempotencyRecordRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void returnsTheOriginalPaymentForAnEquivalentIdempotentRequest() {
        CreatePaymentRequest request = paymentRequest("10.00");

        Payment first = paymentService.create(request, "customer-123", "key-1");
        Payment retry = paymentService.create(request, "customer-123", "key-1");

        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(retry.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void rejectsAnIdempotencyKeyReusedForADifferentRequest() {
        paymentService.create(paymentRequest("10.00"), "customer-123", "key-1");

        assertThatThrownBy(() -> paymentService.create(paymentRequest("20.00"), "customer-123", "key-1"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void allowsOnlyControlledPaymentTransitions() {
        Payment payment = paymentService.create(paymentRequest("10.00"), "customer-123", "key-1");

        Payment approved = paymentService.transition(payment.id(), PaymentStatus.APPROVED);

        assertThat(approved.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThatThrownBy(() -> paymentService.transition(payment.id(), PaymentStatus.REJECTED))
                .isInstanceOf(IllegalStateException.class);
    }

    private CreatePaymentRequest paymentRequest(String amount) {
        return new CreatePaymentRequest("merchant-456", new BigDecimal(amount), "USD");
    }
}
