package com.securepay.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public PaymentService(PaymentRepository paymentRepository, IdempotencyRecordRepository idempotencyRecordRepository) {
        this.paymentRepository = paymentRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Transactional
    public Payment create(CreatePaymentRequest request, String customerId, String idempotencyKey) {
        String fingerprint = fingerprint(customerId, request);
        var existing = idempotencyRecordRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().requestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException();
            }
            return getById(existing.get().paymentId());
        }

        Instant now = Instant.now();
        Payment payment = new Payment(UUID.randomUUID(), customerId, request.merchantId(), request.amount(),
                request.currency(), PaymentStatus.PENDING, now, now);
        paymentRepository.save(new PaymentEntity(payment.id(), payment.customerId(), payment.merchantId(), payment.amount(),
                payment.currency(), payment.status(), payment.createdAt(), payment.updatedAt()));
        idempotencyRecordRepository.save(new IdempotencyRecordEntity(customerId, idempotencyKey, fingerprint, payment.id()));
        return payment;
    }

    public Payment getById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentEntity::toDomain)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    public Payment getForCustomer(UUID paymentId, String customerId) {
        Payment payment = getById(paymentId);
        if (!payment.customerId().equals(customerId)) {
            throw new PaymentNotFoundException(paymentId);
        }
        return payment;
    }

    @Transactional
    public Payment transition(UUID paymentId, PaymentStatus target) {
        PaymentEntity entity = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        Payment transitioned = entity.toDomain().transitionTo(target);
        entity.apply(transitioned);
        return paymentRepository.save(entity).toDomain();
    }

    private String fingerprint(String customerId, CreatePaymentRequest request) {
        String value = customerId + "|" + request.merchantId() + "|" + request.amount().stripTrailingZeros().toPlainString()
                + "|" + request.currency();
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
