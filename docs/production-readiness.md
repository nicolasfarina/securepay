# SecurePay production readiness

Status reviewed: 2026-11-14. This repository is an educational DevSecOps lab; this checklist records what the code can prove and what still needs an actual operating environment or business decision.

## Release decision

**Do not enable real payment processing or deploy this lab as a production payment service yet.** The repository has no production deployment target, payment provider integration, or verified operator controls. Publishing a container image is not a deployment.

## Five operational fronts

| Front | Status | Evidence and next requirement |
|---|---|---|
| Secret scanning and demo credentials | Remediation in progress | Current README examples use placeholders. Local and PostgreSQL profiles now require `SECUREPAY_DEMO_PASSWORD`; tests provide their own credential. Two historical README detections are narrowly fingerprinted in `.gitleaksignore`; the old commit remains in history. Re-run Secret Scan before merging. |
| Dependency update backlog | Blocked by base scan, then revalidate | PRs #53–#56 update ZAP, Prometheus, Temurin, and PostgreSQL image digests. Their CI failures trace to Alertmanager's vulnerable `golang.org/x/net` version in the base branch. This branch raises it to v0.62.0. After merge/rebase, require fresh green checks and review each resulting digest before merging those updates. |
| Production ingress, rate limits, and database operations | Not validated | There is no production ingress/deployment manifest in this repository. Before rollout, record the actual trusted proxy CIDRs and forwarding-header behavior; test limits across replicas under load; rehearse migration, backup restoration, and rollback on the chosen PostgreSQL service. |
| Provider integration, payment state, and reconciliation | Not implemented | The API creates `PENDING` records and has no real provider, tokenized payment method, signed webhook, or executable reconciliation workflow. The `UNKNOWN` runbook assumes provider references and immutable audit events that the service does not yet create. Choose a provider and approve its tokenization, signature/replay, idempotency, audit, retention, and reconciliation contract before implementation. Never send or store raw card data in this service. |
| Monitoring and on-call response | Lab only | Existing metrics and alert rules were exercised in a disposable local stack. Production retention, alert delivery to a staffed channel, external synthetic checks, and approved SLO/thresholds have not been configured or verified. Name an owner and escalation path, then test a page end-to-end. |

## Merge and deployment gates

1. Secret Scan and dependency/image scans pass on the final commit; no broad secret allowlist is added.
2. Maven verification and the PostgreSQL integration suite pass.
3. The four Dependabot PRs are refreshed against the fixed base, reviewed, and pass their own checks.
4. Production ingress, database backup/restore/rollback, and distributed rate-limit behavior are demonstrated in the selected environment.
5. A provider-backed payment flow, signed/replay-safe webhook, actor-attributed immutable audit trail, and provider-verified `UNKNOWN` reconciliation pass integration tests.
6. A real alert reaches the named on-call owner and the response runbook is exercised.

No production deployment can be safely executed from this repository until an actual target environment and the requirements above exist. This is an evidence gap, not a claim that the service is deployed or ready.
