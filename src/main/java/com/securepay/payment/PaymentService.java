package com.securepay.payment;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
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
    private final PaymentMetrics paymentMetrics;
    private final Tracer tracer;

    public PaymentService(PaymentRepository paymentRepository, PaymentCreationTransaction paymentCreationTransaction,
                          PaymentMetrics paymentMetrics, Tracer tracer) {
        this.paymentRepository = paymentRepository;
        this.paymentCreationTransaction = paymentCreationTransaction;
        this.paymentMetrics = paymentMetrics;
        this.tracer = tracer;
    }

    public Payment create(CreatePaymentRequest request, String customerId, String idempotencyKey) {
        Span span = tracer.spanBuilder("payment.create").startSpan();
        try {
            try (var scope = span.makeCurrent()) {
                String fingerprint = fingerprint(customerId, request);
                Payment payment;
                try {
                    payment = paymentCreationTransaction.create(request, customerId, idempotencyKey, fingerprint);
                } catch (DataIntegrityViolationException exception) {
                    // The failed creation has already rolled back, including its payment row.
                    // Read the winner in a fresh transaction; preserve unrelated integrity failures.
                    payment = paymentCreationTransaction.findExisting(customerId, idempotencyKey, fingerprint)
                            .orElseThrow(() -> exception);
                }
                span.setAttribute("payment.status", payment.status().name());
                span.setStatus(StatusCode.OK);
                return payment;
            }
        } catch (RuntimeException exception) {
            span.setAttribute("error.type", exception.getClass().getSimpleName());
            span.setStatus(StatusCode.ERROR);
            throw exception;
        } finally {
            span.end();
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
        Payment current = entity.toDomain();
        Payment transitioned = current.transitionTo(target);
        entity.apply(transitioned);
        Payment saved = paymentRepository.save(entity).toDomain();
        paymentMetrics.recordTransition(current.status(), transitioned.status());
        return saved;
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
