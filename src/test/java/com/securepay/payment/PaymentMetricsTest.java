package com.securepay.payment;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMetricsTest {
    @Test
    void recordsTransitionsWithOnlyBoundedStatusLabels() {
        var registry = new SimpleMeterRegistry();
        var metrics = new PaymentMetrics(registry);

        metrics.recordTransition(PaymentStatus.PENDING, PaymentStatus.APPROVED);

        assertThat(registry.get(PaymentMetrics.TRANSITIONS)
                .tags("from", "PENDING", "to", "APPROVED")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.getMeters()).hasSize(1);
        registry.clear();
    }
}
