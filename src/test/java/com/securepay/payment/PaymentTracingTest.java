package com.securepay.payment;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PaymentTracingTest.TracingTestConfig.class)
class PaymentTracingTest {
    @Autowired private PaymentService paymentService;
    @Autowired private InMemorySpanExporter spanExporter;
    @Autowired private OpenTelemetry openTelemetry;

    @AfterEach
    void clearFinishedSpans() {
        spanExporter.reset();
    }

    @Test
    void recordsSuccessfulPaymentCreationWithoutSensitiveIdentifiers() {
        recordUnrelatedBackgroundSpan();
        var payment = paymentService.create(request("10.00"), "customer-private", "trace-" + UUID.randomUUID());

        var span = paymentSpans().getFirst();
        assertThat(span.getName()).isEqualTo("payment.create");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("payment.status")))
                .isEqualTo("PENDING");
        assertThat(span.getSpanContext().isValid()).isTrue();
        assertThat(span.getAttributes().asMap().keySet())
                .extracting(Object::toString)
                .doesNotContain("payment.id", "customer.id", "merchant.id", "idempotency.key");
        assertThat(paymentSpans()).hasSize(1);
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void recordsFailureTypeWithoutExceptionMessage() {
        String key = "trace-conflict-" + UUID.randomUUID();
        paymentService.create(request("10.00"), "customer-private", key);
        spanExporter.reset();
        recordUnrelatedBackgroundSpan();

        assertThatThrownBy(() -> paymentService.create(request("20.00"), "customer-private", key))
                .isInstanceOf(IdempotencyConflictException.class);

        assertThat(paymentSpans()).hasSize(1);
        var span = paymentSpans().getFirst();
        assertThat(span.getName()).isEqualTo("payment.create");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("error.type")))
                .isEqualTo("IdempotencyConflictException");
        assertThat(span.getEvents()).isEmpty();
    }

    private static CreatePaymentRequest request(String amount) {
        return new CreatePaymentRequest("merchant-private", new BigDecimal(amount), "USD");
    }

    private java.util.List<io.opentelemetry.sdk.trace.data.SpanData> paymentSpans() {
        return spanExporter.getFinishedSpanItems().stream()
                .filter(span -> span.getName().equals("payment.create"))
                .toList();
    }

    private void recordUnrelatedBackgroundSpan() {
        // Scheduled tasks share this exporter. Their ordering must not affect payment assertions.
        openTelemetry.getTracer("background-test").spanBuilder("task tokenRevocationService.removeExpiredRevocations")
                .startSpan().end();
    }

    @TestConfiguration
    static class TracingTestConfig {
        @Bean
        InMemorySpanExporter spanExporter() {
            return InMemorySpanExporter.create();
        }

        @Bean
        SdkTracerProvider testTracerProvider(InMemorySpanExporter exporter) {
            return SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build();
        }

        @Bean
        @Primary
        OpenTelemetry testOpenTelemetry(SdkTracerProvider tracerProvider) {
            return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
        }
    }
}
