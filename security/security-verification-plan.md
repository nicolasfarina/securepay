# SecurePay Security Verification Plan

Spanish version: [Leer en español](security-verification-plan.es.md)

## Purpose

This plan defines the minimum evidence needed to show that the controls in the [security risk register](risk-register.md) work as intended. It complements functional tests; it does not replace manual security review.

## Test Levels

| Level | When it runs | Goal |
|---|---|---|
| Unit | Every change | Verify validation, authorization decisions, state transitions, and data masking in isolation. |
| Integration | Pull request and release candidate | Verify database constraints, provider boundaries, tokenization, and webhook verification. |
| End-to-end | Release candidate | Verify the complete payment lifecycle, including failures and reconciliation. |
| Operational | Before production and periodically | Verify access, secret rotation, alerting, audit retrieval, and incident runbooks. |

## Required Verification by Risk

| Risk | Automated checks | Manual / operational checks | Passing condition |
|---|---|---|---|
| PAY-01 Unauthorized payment | Requests without credentials, with invalid credentials, and for another customer's payment return no payment data and create no payment. | Review roles and privileged account access. | Only authorized actors can perform each operation. |
| PAY-02 Duplicate or replayed payment | Submit identical requests concurrently; retry after a client timeout; reuse a key with a changed request body. | Review idempotency-key expiry and storage retention. | One provider charge at most; equivalent retries return the original outcome; mismatched reuse is rejected. |
| PAY-03 Sensitive-data exposure | Assert logs, error bodies, traces, and persistence omit PAN and CVV; scan built artifacts for secrets. | Inspect provider tokenization configuration. | No prohibited payment data or credentials appear outside approved secret/token systems. |
| PAY-04 Tampering | Alter amount, currency, ownership, status, and webhook signature. | Review allowed state-transition policy. | Invalid input and unsigned/invalid webhooks are rejected; illegal transitions cannot persist. |
| PAY-05 Unknown provider outcome | Simulate connection loss and provider timeout before and after provider processing. | Exercise the reconciliation runbook using a controlled record. | Outcome stays `UNKNOWN` until reconciliation resolves it; reconciliation is idempotent and audited. |
| PAY-06 Repudiation | Assert successful and failed sensitive actions emit an audit event with actor, payment ID, time, result, and trace ID. | Retrieve an event as an approved investigator; verify retention settings. | The event is complete, searchable, and protected from routine mutation. |
| PAY-07 Resource exhaustion | Test rate, payload-size, timeout, and concurrency limits. | Verify dashboards and alerts using a controlled threshold breach. | Excess traffic is constrained without exposing data or destabilizing payment processing. |
| PAY-08 Secret exposure | Run secret scanning and dependency/image scanning in CI. | Review runtime secret access and perform a rotation exercise. | No committed secrets; only approved workload identities can read required secrets. |

## CI Quality Gates

Every pull request that changes payment, authentication, authorization, persistence, webhooks, or infrastructure must run:

- Unit and integration tests for the affected control.
- Static analysis and dependency vulnerability scanning.
- Secret scanning of source and generated configuration.
- Tests that prevent logging or returning prohibited payment data.

Release candidates must additionally run the end-to-end `UNKNOWN` reconciliation scenario and the idempotency concurrency scenario.

The operational procedure is defined in the [`UNKNOWN` payment reconciliation runbook](runbooks/unknown-payment-reconciliation.md).

## Evidence and Retention

Attach test results to the build or release record. Store operational-review evidence with its date, reviewer, scope, outcome, and remediation issue for failures. A failed Critical-risk check blocks release until fixed or formally accepted under the risk-register exception process.
