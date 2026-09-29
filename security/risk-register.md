# SecurePay Security Risk Register

Spanish version: [Leer en español](risk-register.es.md)

## Purpose

This register converts the payment-flow threat model into security work that can be owned, tested, and reviewed before release. Risk ratings are qualitative and must be revisited when the architecture, provider, or data classification changes.

## Rating Method

| Rating | Meaning |
|---|---|
| Critical | Likely to cause fraud, broad sensitive-data exposure, or loss of control of production systems. Blocks release until mitigated. |
| High | Could materially affect payment integrity, customer data, or availability. Requires a documented mitigation and release approval. |
| Medium | Meaningful but contained impact. Mitigate in the planned delivery cycle and track to closure. |

## Active Risks

| ID | Risk | Rating | Required controls | Validation evidence | Owner |
|---|---|---|---|---|---|
| PAY-01 | An unauthenticated or unauthorized caller creates a payment. | Critical | Authenticate every request; enforce customer and merchant ownership; use least-privilege roles. | Automated authorization tests; access-review record. | API team |
| PAY-02 | A retry or replay causes a duplicate charge. | Critical | Require an idempotency key; bind it to the authenticated caller and request fingerprint; enforce unique storage and safe response replay. | Concurrent duplicate-request test; database constraint review. | Payments team |
| PAY-03 | Card or other sensitive data is exposed. | Critical | Accept provider tokens only; never store PAN or CVV; encrypt approved data stores; mask logs and error responses. | Log scan; schema review; tokenization integration test. | Payments team |
| PAY-04 | Payment state or amount is tampered with. | High | Validate amount, currency, merchant, and ownership server-side; restrict state transitions; verify provider webhook signatures. | Negative API tests; transition tests; webhook-signature test. | Payments team |
| PAY-05 | An unavailable or delayed provider response leads to an incorrect final state. | High | Use `UNKNOWN` for unresolved outcomes; reconcile against the provider; make reconciliation idempotent and auditable. | Simulated timeout test; reconciliation runbook exercise. | Payments team |
| PAY-06 | Sensitive actions cannot be investigated or are repudiated. | Medium | Emit immutable audit events with actor, payment ID, outcome, timestamp, and trace ID; restrict audit-log access. | Audit-event test; retention and access review. | Platform team |
| PAY-07 | The API is exhausted by abusive traffic. | High | Apply rate limits and quotas by caller; set payload limits and timeouts; monitor saturation and failed requests. | Load/limit test; alert verification. | Platform team |
| PAY-08 | Secrets or provider credentials are exposed or over-privileged. | Critical | Store secrets in managed secret storage; rotate credentials; prohibit secrets in source, images, and logs; scope provider credentials minimally. | Secret scan; access-policy review; rotation exercise. | Platform team |

## Release Gates

A payment-service release must not proceed unless:

- No Critical risk is open or accepted without explicit security leadership approval.
- Authorization, idempotency, state-transition, and webhook-verification tests pass.
- Logs, API errors, and database schema have been reviewed for prohibited payment data.
- `UNKNOWN` payment reconciliation has an owner, dashboard, and tested runbook.
- Secrets are supplied only through approved runtime secret management.

## Exceptions

Every risk acceptance must name the risk ID, business justification, compensating controls, approving authority, expiry date, and remediation issue. Expired exceptions block the affected release.
