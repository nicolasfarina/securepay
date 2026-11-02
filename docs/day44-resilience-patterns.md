# Day 44 — Timeouts, retries, circuit breakers, and bulkheads

## Scope

SecurePay currently has no production outbound provider call. This exercise
therefore verifies four resilience policies against deterministic test doubles;
it does not add a provider, change payment behavior, or alter runtime
configuration. Resilience4j is included with test scope only.

The tests demonstrate:

- **Timeout:** a bounded wait for a future, with cancellation requested when the
  deadline expires.
- **Retry:** at most three attempts for an explicitly classified transient
  failure.
- **Circuit breaker:** after two failed calls, the breaker opens and rejects the
  next call without reaching the dependency.
- **Bulkhead:** one concurrent call is admitted while a second is rejected.

The implementation uses the Resilience4j core modules. Spring Boot integration
is intentionally not enabled. If a real provider call is introduced later,
choose its timeout from a latency budget; connect/read timeouts belong on the
HTTP transport. Cancellation of a future does not prove that a blocking network
request stopped.

## Safety and payment limits

No external or production endpoint is called. No payment endpoint uses these
policies. Do not apply automatic retries to payment writes unless the full
operation is idempotent and the downstream's idempotency behavior is verified.
Retry only known transient errors; validation, authorization, and business
declines must not be retried. Circuit-breaker and bulkhead thresholds require
representative traffic measurements before use in a production integration.

## Verification

The focused suite is `Day44ResiliencePatternsTest`. Its suppliers, counters,
latches, and futures are local test objects. It checks the observable outcomes
and does not start Docker, a server, or an external dependency.

`mvn -B -ntp -Dtest=Day44ResiliencePatternsTest test` passed on November 2,
2026: 4 tests, 0 failures, 0 errors, 0 skipped.
The complete local `mvn -B -ntp test` suite also passed: 169 tests, 0 failures,
0 errors, and 8 skipped (PostgreSQL-only checks that require the separate
PostgreSQL test run).

## References

- [Resilience4j core modules and decorators](https://github.com/resilience4j/resilience4j)
- [Resilience4j getting started guide](https://resilience4j.readme.io/docs/getting-started)

