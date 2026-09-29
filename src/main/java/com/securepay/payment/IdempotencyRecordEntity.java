package com.securepay.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = @UniqueConstraint(
        name = "idempotency_records_customer_key_unique", columnNames = {"customer_id", "idempotency_key"}))
class IdempotencyRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecordEntity() {
    }

    IdempotencyRecordEntity(String customerId, String idempotencyKey, String requestFingerprint, UUID paymentId) {
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.paymentId = paymentId;
        this.createdAt = Instant.now();
    }

    String requestFingerprint() {
        return requestFingerprint;
    }

    UUID paymentId() {
        return paymentId;
    }
}
