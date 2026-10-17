package com.securepay.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/** Isolates creation and replay from failed transactions, including a caller's transaction. */
@Service
public class PaymentCreationTransaction {
    private final PaymentRepository payments;
    private final IdempotencyRecordRepository records;

    public PaymentCreationTransaction(PaymentRepository payments, IdempotencyRecordRepository records) {
        this.payments = payments;
        this.records = records;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment create(CreatePaymentRequest request, String customerId, String key, String fingerprint) {
        var existing = lookup(customerId, key, fingerprint);
        if (existing.isPresent()) return existing.get();

        // Match the database precision so the first response and later replays agree.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Payment payment = new Payment(UUID.randomUUID(), customerId, request.merchantId(), request.amount(),
                request.currency(), PaymentStatus.PENDING, now, now);
        payments.save(new PaymentEntity(payment.id(), payment.customerId(), payment.merchantId(), payment.amount(),
                payment.currency(), payment.status(), payment.createdAt(), payment.updatedAt()));
        records.saveAndFlush(new IdempotencyRecordEntity(customerId, key, fingerprint, payment.id()));
        return payment;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Payment> findExisting(String customerId, String key, String fingerprint) {
        return lookup(customerId, key, fingerprint);
    }

    private Optional<Payment> lookup(String customerId, String key, String fingerprint) {
        return records.findByCustomerIdAndIdempotencyKey(customerId, key).map(record -> {
            if (!record.requestFingerprint().equals(fingerprint)) throw new IdempotencyConflictException();
            return payments.findById(record.paymentId()).map(PaymentEntity::toDomain)
                    .orElseThrow(() -> new PaymentNotFoundException(record.paymentId()));
        });
    }
}
