package com.securepay.payment;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentCreationTransaction paymentCreationTransaction;

    public PaymentService(PaymentRepository paymentRepository, PaymentCreationTransaction paymentCreationTransaction) {
        this.paymentRepository = paymentRepository;
        this.paymentCreationTransaction = paymentCreationTransaction;
    }

    public Payment create(CreatePaymentRequest request, String customerId, String idempotencyKey) {
        String fingerprint = fingerprint(customerId, request);
        try {
            return paymentCreationTransaction.create(request, customerId, idempotencyKey, fingerprint);
        } catch (DataIntegrityViolationException exception) {
            // The failed creation has already rolled back, including its payment row.
            // Read the winner in a fresh transaction; preserve unrelated integrity failures.
            return paymentCreationTransaction.findExisting(customerId, idempotencyKey, fingerprint)
                    .orElseThrow(() -> exception);
        }
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

    public List<Payment> listAll() {
        return paymentRepository.findAll().stream()
                .map(PaymentEntity::toDomain)
                .toList();
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
