# Runbook: `UNKNOWN` Payment Reconciliation

Spanish version: [Leer en español](unknown-payment-reconciliation.es.md)

## When to Use This Runbook

Use this runbook when SecurePay cannot determine a final payment result, for example after a provider timeout, network interruption, or an unconfirmed webhook. Do not mark the payment `APPROVED` or `REJECTED` based only on an assumed outcome.

## Preconditions

- The operator has the approved payments-operations role.
- The payment record, audit events, trace ID, provider reference, and idempotency key are available.
- Provider-console access follows least privilege and is audited.

## Procedure

1. Locate the payment by its SecurePay payment ID. Confirm the state is `UNKNOWN`; do not create a replacement payment.
2. Collect the immutable context: customer and merchant identifiers, amount, currency, creation time, provider reference, idempotency-key hash, and trace ID. Do not copy tokens or sensitive payment data into tickets or chat.
3. Query the provider using the provider reference. If it is unavailable, use the provider's approved idempotency or merchant lookup mechanism. Record the provider query time and result in the audit trail.
4. Apply the result exactly once:
   - Provider confirms a successful charge: transition to `APPROVED`.
   - Provider confirms a definitive failure with no charge: transition to `REJECTED`.
   - Provider result remains absent, ambiguous, or inconsistent: keep `UNKNOWN` and escalate.
5. Verify that the resulting state, provider reference, reconciliation actor, source, timestamp, and trace ID appear in the audit log.
6. Notify the customer only through the approved customer-communications flow. Do not promise a final outcome while the payment remains `UNKNOWN`.

## Escalation

Escalate to the payments on-call engineer and the provider support channel when:

- The provider cannot confirm an outcome within the agreed service window.
- SecurePay and provider records disagree.
- More than one reconciliation attempt reports different results.
- A reconciliation reveals a duplicate or potentially fraudulent charge.

Escalate a suspected data exposure, unauthorized access, or credential issue through the security incident process immediately.

## Guardrails

- Reconciliation must be idempotent: repeating the operation cannot create an additional charge or duplicate audit event.
- Only the reconciliation workflow may resolve `UNKNOWN`; manual database edits are prohibited.
- Never retry a new provider charge solely because a previous result is unknown.
- Preserve original provider responses and audit references according to the retention policy.

## Completion Criteria

The run is complete only when the payment has a provider-verified final state or remains explicitly `UNKNOWN` with an active escalation record. Link the supporting audit event and provider case/reference to the operational record.
