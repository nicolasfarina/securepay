# SecurePay production readiness

Status reviewed: 2026-11-14. This repository is an educational DevSecOps lab; this checklist records what the code can prove and what still needs an actual operating environment or business decision.

## Release decision

**Do not enable real payment processing or deploy this lab as a production payment service yet.** The repository has no production deployment target, payment provider integration, or verified operator controls. Publishing a container image is not a deployment.

## Five operational fronts

| Front | Status | Evidence and next requirement |
|---|---|---|
| Secret scanning and demo credentials | Implemented in PR #57; remote check passed | Current README examples use placeholders. Local and PostgreSQL profiles require `SECUREPAY_DEMO_PASSWORD`; tests provide their own credential. Two historical README detections are narrowly fingerprinted in `.gitleaksignore`; the old commit remains in history. Secret Scan passed for the PR head. |
| Dependency update backlog | Known upstream Prometheus finding; remediation pending | PR #57 raises Alertmanager `golang.org/x/net` to v0.62.0 and incorporates the Temurin digest from #55 and Prometheus v3.16.0 digest from #54. Trivy reports that the official Prometheus 3.16.0 binary still embeds `golang.org/x/net` v0.61.0 (CVE-2026-78661, HIGH; fixed in v0.62.0). Trivy remains visible and failing under the temporary merge policy; no finding is suppressed. Move to a patched official image before restoring the required security check or considering production deployment. |
| Production ingress, rate limits, and database operations | Not validated | There is no production ingress/deployment manifest in this repository. Before rollout, record the actual trusted proxy CIDRs and forwarding-header behavior; test limits across replicas under load; rehearse migration, backup restoration, and rollback on the chosen PostgreSQL service. |
| Provider integration, payment state, and reconciliation | Not implemented | The API creates `PENDING` records and has no real provider, tokenized payment method, signed webhook, or executable reconciliation workflow. The `UNKNOWN` runbook assumes provider references and immutable audit events that the service does not yet create. Choose a provider and approve its tokenization, signature/replay, idempotency, audit, retention, and reconciliation contract before implementation. Never send or store raw card data in this service. |
| Monitoring and on-call response | Lab only | Existing metrics and alert rules were exercised in a disposable local stack. Production retention, alert delivery to a staffed channel, external synthetic checks, and approved SLO/thresholds have not been configured or verified. Name an owner and escalation path, then test a page end-to-end. |

## Merge and deployment gates

1. Secret Scan and functional checks pass on the final commit. Trivy remains visible and enforced by its own failing `security` check during the temporary merge policy; no vulnerability suppression is added.
2. Maven verification and the PostgreSQL integration suite pass.
3. Prometheus ships an official image embedding `golang.org/x/net` v0.62.0 or newer. Refresh and review image updates, then restore `security` as a required check before treating the dependency backlog as cleared.
4. Production ingress, database backup/restore/rollback, and distributed rate-limit behavior are demonstrated in the selected environment.
5. A provider-backed payment flow, signed/replay-safe webhook, actor-attributed immutable audit trail, and provider-verified `UNKNOWN` reconciliation pass integration tests.
6. A real alert reaches the named on-call owner and the response runbook is exercised.

No production deployment can be safely executed from this repository until an actual target environment and the requirements above exist. This is an evidence gap, not a claim that the service is deployed or ready.
