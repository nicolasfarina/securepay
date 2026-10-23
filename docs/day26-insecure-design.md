# Day 26 — Insecure Design and business-logic abuse cases

Date: 2026-10-23 (America/Buenos_Aires).

Reviewed revision: `main` at `2231f8ec4d82997e47819e79f461437635a0f1d2`. This is a static design review of the root API, payment model, persistence constraints, existing tests, threat model, and risk register. It is not a penetration test or production assessment.

## Scope and observed flow

An authenticated `CUSTOMER` submits `merchantId`, `amount`, and `currency` to `POST /api/payments` with an `Idempotency-Key`. The API creates a `PENDING` payment using those submitted values. A customer can retrieve only their own payment; an `ADMIN` can list payments. There is no merchant registry, order/quote source, payment-provider integration, or public state-transition endpoint in the reviewed root API.

`PaymentService` and `PaymentCreationTransaction` bind an idempotency record to `(customer_id, idempotency_key)` and a fingerprint of customer, merchant, amount, and currency. Reusing the same key and payload replays the original payment; changing the payload conflicts. The PostgreSQL suite exercises concurrent equivalent requests, concurrent conflicting payloads, normalization of decimal scale, cross-customer key reuse, and unrelated integrity failures. `PaymentStatus` also enforces an explicit transition graph.

## Abuse-case review

| Scenario | What the implementation currently does | Design assessment and next validation |
|---|---|---|
| A customer changes the amount or names an arbitrary merchant. | The authenticated customer supplies both fields. Validation checks shape and numeric bounds, but there is no server-side order price, merchant lookup, or customer-to-merchant relationship. | **Decision required.** This is acceptable only as a learning/demo API. A real purchase flow needs a trusted order/quote and an authorized merchant relationship before creating a payment. Do not add an allowlist or price rule until that business contract is defined. |
| A customer creates many payments using a fresh key for every request. | Idempotency prevents duplicate processing for a reused key; it does not limit distinct requests or total exposure. The service has no customer/merchant quota or maximum transaction total. | **Open abuse path.** Define transaction and velocity limits. Rate limiting is already tracked for Day 34 / PAY-07; a rate limit alone will not establish an order price or spending policy. |
| A client retries the same request concurrently, or changes the payload while reusing the key. | The database uniqueness constraint and transaction recovery return one payment for equivalent requests; a different fingerprint becomes a conflict. Existing PostgreSQL tests cover both cases. | **Control present and tested.** Preserve these invariants when payment creation changes. |
| A client attempts to set or skip payment status. | There is no public endpoint for status changes. The domain rejects invalid transitions; the service transition method is internal to the application surface reviewed. | **No current public route identified.** Before adding provider callbacks or refund operations, authenticate the source, bind updates to a payment/provider event, make event replay safe, and define reconciliation for `UNKNOWN`. |
| A customer retrieves another customer's payment or uses the admin listing. | Customer retrieval is scoped to the authenticated owner; the admin list requires the `ADMIN` role. Days 23–24 tests cover ownership and role checks. | **Control present and tested.** Keep ownership checks server-side and retain the non-disclosing not-found response for another customer's payment. |

## Decisions needed before functional controls

1. Is the current API strictly a demo/learning endpoint, or should it model a real purchase flow? If real, what server-side object supplies the authoritative amount and merchant?
2. Which currencies, amount bounds, decimal precision, and customer/merchant exposure limits are part of the intended contract?
3. When an outcome is `UNKNOWN`, which trusted provider event or reconciliation process may move it to `APPROVED` or `REJECTED`, and how are duplicate or late events handled?

Until those rules are agreed, this session changes documentation only. It does not claim a confirmed production vulnerability, add payment restrictions, or present the project as ready to process real payments.

## Test charter after the rules are decided

- Reject a merchant that is not onboarded or not associated with the customer/order.
- Reject a client amount that differs from the server-side quote; verify currency and decimal/limit rules.
- Verify that per-customer and per-merchant limits behave correctly under concurrent requests.
- Preserve equivalent-key replay and changed-payload conflict behavior under concurrency.
- Accept a payment-state update only from the authorized provider/reconciliation path; reject duplicate or out-of-order provider events safely.

The first three checks need business requirements before expected results can be asserted. Existing idempotency and ownership tests remain regression controls; this review does not duplicate them.
