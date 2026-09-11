package com.securepay.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Payment {

    private final UUID id;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus status;
    private final String idempotencyKey;
    private final Instant createdAt;

    private Payment(UUID id, BigDecimal amount, String currency, PaymentStatus status,
                    String idempotencyKey, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.amount = validateAmount(amount);
        this.currency = requireText(currency, "currency");
        this.status = Objects.requireNonNull(status, "status is required");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static Payment create(BigDecimal amount, String currency, String idempotencyKey) {
        return new Payment(UUID.randomUUID(), amount, currency, PaymentStatus.PENDING,
                idempotencyKey, Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static BigDecimal validateAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        return amount;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
