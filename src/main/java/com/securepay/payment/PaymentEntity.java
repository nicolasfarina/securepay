package com.securepay.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
class PaymentEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @Column(name = "merchant_id", nullable = false, length = 128)
    private String merchantId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentEntity() {
    }

    PaymentEntity(UUID id, String customerId, String merchantId, BigDecimal amount, String currency, PaymentStatus status,
                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    Payment toDomain() {
        return new Payment(id, customerId, merchantId, amount, currency, status, createdAt, updatedAt);
    }

    void apply(Payment payment) {
        status = payment.status();
        updatedAt = payment.updatedAt();
    }
}
