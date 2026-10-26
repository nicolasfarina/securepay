# Day 39 — Distributed Tracing

## Goal

Connect the OpenTelemetry span from Day 38 to inbound and outbound work, preserve trace context across executor threads, and keep trace export configurable for a collector.

## Implementation

- Spring Boot's OpenTelemetry starter provides the tracing SDK integration. Sampling is configurable through `SECUREPAY_TRACING_SAMPLING_PROBABILITY` and defaults to 10%, matching Spring Boot's default sampling rate.
- W3C Trace Context is the propagation format. The `OpenTelemetryRestClientCustomizer` adds a `traceparent` header from the current OpenTelemetry context to outgoing `RestClient` requests when the request does not already have one. Applications should use Spring Boot's auto-configured `RestClient.Builder` so Boot's observation instrumentation is applied too.
- OTLP trace export is disabled by default. Set `SECUREPAY_TRACING_OTLP_ENABLED=true` to enable it, then configure the collector endpoint with `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` or `OTEL_EXPORTER_OTLP_ENDPOINT`. Credentials and collector addresses belong in deployment configuration, not source control.
- `MdcTaskDecorator` now copies both MDC and OpenTelemetry `Context` into executor tasks and restores the worker thread's prior contexts after completion or failure.
- Trace IDs, span IDs, correlation IDs, payment IDs, and exception messages are not added as metric tags.

## Verification

- `TracePropagationHttpTest` sends an authenticated MockMvc request with a W3C `traceparent`, extracts that context in a test-only probe controller, and performs a real loopback HTTP request using a `RestClient` with the production customizer. The test checks that the outgoing header retains the same trace and parent span IDs. It does not create a downstream production integration or independently prove a newly created client span; production `RestClient` calls should use Boot's auto-configured builder for that instrumentation.
- `MdcTaskDecoratorTest` checks that both the correlation MDC and current trace context cross an executor boundary and that the worker's prior state is restored.
- Spring Boot's OTLP configuration properties and environment variable mappings are documented in the [tracing reference](https://docs.spring.io/spring-boot/reference/actuator/tracing.html) and [observability reference](https://docs.spring.io/spring-boot/reference/actuator/observability.html).

## Operational notes

Sampling at 10% reduces the volume sent to a trace backend; operators can tune it for the environment. OTLP remains off until explicitly enabled and an endpoint is configured. The loopback HTTP test uses H2 and is focused on propagation; the repository's PostgreSQL integration script covers the full application suite separately. Trace-context propagation does not make arbitrary unmanaged threads safe: asynchronous work should use Spring-managed executors with the configured task decorator.
