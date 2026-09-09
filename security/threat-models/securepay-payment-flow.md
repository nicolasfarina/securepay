# SecurePay Payment Flow Threat Model

Spanish version: [Leer en español](securepay-payment-flow.es.md)

## Scope

This model covers the flow from the client payment request to the payment provider and the final payment status.

## Assets

- Payment data and transaction details
- Customer identity and authorization data
- Idempotency keys
- Payment status and provider responses
- Audit logs

## Trust Boundaries

1. Client application to SecurePay API
2. SecurePay services to the database
3. SecurePay payment service to the external payment provider
4. SecurePay operators to production infrastructure

## Main Threats

| Threat | Impact | Initial control |
|---|---|---|
| Unauthorized payment request | Fraudulent charges | Strong authentication, authorization, and transaction validation |
| Duplicate payment request | Customer charged twice | Idempotency key stored and checked before processing |
| Payment data exposure | Financial and privacy damage | TLS, encryption at rest, tokenization, and secret management |
| Payment status tampering | Incorrect business decision | Server-side state transitions and signed provider responses |
| Replay of an old request | Repeated or fraudulent operation | Idempotency, request expiration, and authentication |
| Missing provider response | Inconsistent payment state | `UNKNOWN` status and reconciliation process |
| Sensitive data in logs | Data leakage | Structured logs with masking and access controls |

## Security Requirements

- Never store raw card numbers or CVV in SecurePay.
- Require authentication and authorization for every payment operation.
- Validate amount, currency, merchant, and ownership on the server side.
- Enforce a unique `Idempotency-Key` for each payment operation.
- Allow payment status changes only through controlled transitions.
- Record security-relevant events without logging sensitive payment data.
- Reconcile `UNKNOWN` payments with the provider before final resolution.

## Residual Risk

An external provider may approve a payment while SecurePay is temporarily unable to receive the response. SecurePay must keep the payment as `UNKNOWN` and reconcile it instead of blindly retrying or marking it as `REJECTED`.
