# ADR 0001: Payment Lifecycle and Idempotency

Spanish version: [Leer en español](0001-payment-lifecycle-and-idempotency.es.md)

## Status

Accepted

## Context

SecurePay processes credit card payments. A payment may take time to receive a final response from the payment provider.

The system must prevent duplicate charges when a client retries the same request because of a network error or timeout.

## Decision

Every payment request must include an `Idempotency-Key`.

SecurePay will process the first request with a unique key. If it receives another request with the same key, it will not create another payment. It will return the same result from the original request.

## Payment Status Flow

```text
PENDING
   ├── APPROVED
   ├── REJECTED
   └── UNKNOWN
          ├── APPROVED
          └── REJECTED
```

`REFUNDED` can only happen after `APPROVED`.

## Consequences

- Duplicate charges are prevented.
- Clients can retry safely.
- The system needs to store idempotency keys and payment results.
- Payments in `UNKNOWN` require reconciliation with the payment provider.
