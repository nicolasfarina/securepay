# SecurePay — 90 Day DevSecOps & Cloud Security Roadmap

**Current focus:** Day 42 — actionable alerting. Days 36–40 are implemented on the current feature branch; Day 41 defines proposed objectives and telemetry gaps. Day 41's proposed objectives are not production commitments; they need representative measurements and operator approval. Day 35 API negative/security tests were completed and merged on October 24, 2026. Day 34 production thresholds, trusted proxy configuration and shared counters for multiple replicas remain open operational work. The documentary review pass for Days 26–33 is merged, while practical follow-ups remain open.

Day 35 was completed on October 24, 2026. PR #36 introduced six HTTP negative-security cases for untrusted forwarding headers, spoofed identity headers, and invalid Authorization schemes; PR #36 was merged into the Brotli-fix branch and then reached main through PR #35 (main commit `16f5f8b6b85356d25cbc474b6075ff4219882a15`). GitHub Actions [main CI #40109195344](https://github.com/nicolasfarina/securepay/actions/runs/40109195344) passed after the merge, including container and dependency Trivy scans, Maven verify (74 counted, 69 passed and 5 PostgreSQL-only cases skipped), PostgreSQL selection (49 passed, 0 skipped), and SpotBugs. The six `ApiNegativeSecurityTest` cases all passed. Secret Scan and container publishing also passed on the same main commit. This validates the observed tests and CI but not production reverse-proxy trust settings or all possible API attacks. See [Day 35 evidence and limitations](docs/day35-api-negative-tests.md).

Day 25 was completed as a guided learning session in Spanish on October 22, 2026. The assistant reviewed root application input and persistence paths, added focused SQL-looking input regression tests, updated the A03 evidence and CI PostgreSQL test selection, and ran `mvn -B -ntp -Duser.timezone=UTC verify` locally: 64 cases counted, 59 passed, 5 PostgreSQL-only cases skipped, no failures/errors, and SpotBugs found 0 issues. The PostgreSQL integration selection also passed locally against a new disposable PostgreSQL 18.6 container: 49 passed, no failures/errors/skips, and SpotBugs found 0 issues. PR #29 was merged to `main` (merge commit `2e55598b9a46a4ed925a37011f4c9e17da4e5e06`); CI #104 and Secret Scan #92 passed before the merge. The learner's answers and participation in the lesson are not represented as independent test execution. No production validation rules were changed because merchant, username/password and idempotency-key character policies are not specified; log sanitization is deferred because the root app has no application log consumer. See [Day 25 injection inventory and test evidence](docs/day25-injection.md).

Days 23 and 24 were completed as guided learning sessions in Spanish on October 21, 2026. The learner ran the existing ownership and customer/admin role tests, the JWT payload test, and the six-case invalid-token test (6 passed, no failures/errors/skips). The learner then authorized closing the application coverage gaps: 5 new HTTP cases cover the payment-read matrix for both customers, supplied foreign ownership, hidden/missing errors and a JWT altered without a new signature. Assistant verification with H2 passed `mvn -B -ntp verify`: 61 cases counted, 56 passed, 5 PostgreSQL-only cases skipped, no failures/errors or SpotBugs findings. Production logic was unchanged. Source review, learner evidence and assistant verification are distinguished in [Day 23 evidence](docs/day23-access-control.md) and [Day 24 evidence and limits](docs/day24-cryptographic-failures.md). Operational key rotation, TLS, encryption-at-rest verification and later design/hardening reviews remain open.

Day 22 is complete as of October 21, 2026: the initial OWASP Top 10:2021 map records existing controls, evidence and open work, with a cross-reference to the 2025 edition. The real HTTP ownership test passed, and the related H2 integration suites passed all 51 tests (0 failures, 0 errors, 0 skipped). These local checks were executed by the assistant; they do not replace the learner's independent practice or PostgreSQL/operational verification. See [Day 22 mapping, results and next-session plan](docs/day22-owasp-mapping.md). Application and test code were unchanged.

Day 10 is verified and merged into `main` through PR #2. See [verification evidence and reproduction steps](docs/day10-postgres-verification.md).

Days 11 and 12 are verified and their PRs #3 and #4 have been merged into the dependency branches. This work starts from `fdf2761` on `codex/day11-integration-tests`, containing both days; at Day 13 verification, `main` still contains Day 10. See [Day 11 coverage](docs/day11-integration-verification.md) and [Day 12 packaging evidence](docs/day12-container-verification.md).

Days 13–21 are implemented, verified, and integrated into `main`. This work includes the Day 13 Compose setup, Days 14–18 configuration and security checks, Day 19 image scanning, Day 20 SBOM generation, and Day 21 CI/branch-protection controls. Verification on October 17: `mvn -B verify` passed (56 tests; 5 PostgreSQL-only tests skipped without explicit configuration); `scripts/test-integration.ps1` passed all 56 tests against PostgreSQL 18.6, including concurrency; `scripts/test-container.ps1` passed production-profile, non-root, read-only filesystem, payment persistence, and graceful shutdown checks; and `scripts/test-compose.ps1` passed API/stack recreation, payment persistence, database outage readiness/liveness, health recovery, and single-payment/idempotency counts. The Compose script now waits for Docker health status after service recreation, avoiding a flaky `compose up --wait` result. Maven verification also generated the application CycloneDX SBOM and passed SpotBugs with no findings. See [Day 13 verification and lesson](docs/day13-compose-verification.md), [Day 19 container scanning](docs/day19-container-scanning.md), [Day 20 SBOM notes](docs/day20-sbom.md), and [Day 21 pipeline and branch rules](docs/day21-pipeline-and-branch-rules.md).

Day 14 externalized configuration and environment profiles are implemented. Local verification is recorded in [Day 14 verification](docs/day14-configuration-verification.md).

Day 15 continuous integration workflow is implemented. It runs the H2 suite and a PostgreSQL-backed verification job on pull requests and selected branch pushes; configure repository branch protection to require the `verify` job. See [Day 15 verification](docs/day15-ci-verification.md).

Day 16 static security analysis is enforced during Maven `verify` and in CI using SpotBugs with the OWASP Find Security Bugs detectors. See [Day 16 verification](docs/day16-static-analysis.md).

Day 17 dependency scanning is configured for Maven manifests in CI, with weekly Dependabot update checks for Maven modules, Docker images, Compose images, and GitHub Actions. Fixed high or critical findings fail CI. See [Day 17 verification](docs/day17-dependency-scanning.md).

Day 18 scans pull requests, branch pushes, and full Git history for secrets with Gitleaks. Local environment files are ignored by Git, and the README documents secret rotation response. See [Day 18 verification](docs/day18-secret-scanning.md).

Day 19 builds the runtime image in CI and scans it for fixed high and critical OS/library vulnerabilities. The non-root runtime and Compose filesystem/capability restrictions remain in force. See [Day 19 verification](docs/day19-container-scanning.md).

Day 20 generates a CycloneDX SBOM for application dependencies and the final runtime image, then uploads both inventories with CI verification artifacts. See [Day 20 SBOM notes](docs/day20-sbom.md).

Day 21 is complete: CI and secret scanning run on PRs and active feature branches, the active `Protect main` ruleset requires PRs plus up-to-date `verify` and `gitleaks` checks, and the PostgreSQL 18 volume layout is covered by the updated Compose verification. See [Day 21 pipeline and branch rules](docs/day21-pipeline-and-branch-rules.md) and [PostgreSQL 18 volume and migration notes](docs/postgres-18-volume-migration.md).

## Stage 1 — SecurePay Foundations

| Day | Goal |
|---|---|
| **Day 1** | Payment flow: `PENDING`, `APPROVED`, `REJECTED`, `UNKNOWN`, `REFUNDED`. Understand retries and idempotency. |
| **Day 2** | Create repository, bootstrap project, README and first commit. |
| **Day 3** | `Payment` domain, validations, service layer and unit tests. |
| **Day 4** | REST API: requests/responses, HTTP status codes and documentation. |
| **Day 5** | Persistence with JPA + PostgreSQL + Flyway. |
| **Day 6** | Centralized error handling and standard API error format. |
| **Day 7** | Threat Modeling for the payment flow + initial Risk Register. |
| **Day 8** | Baseline security controls, roles and password policy. |
| **Day 9 ✅** | JWT Authentication & Authorization. |
| **Day 10** | Real payment idempotency and duplicate payment prevention. |
| **Day 11** | Complete integration tests. |

### Day 9 — Completed Scope

- `User`
- `UserRole`
- `CUSTOMER`
- `ADMIN`
- BCrypt
- `POST /auth/login`
- JWT generation
- Bearer Token
- JWT validation
- JWT Filter
- Spring Security
- `401 Unauthorized`
- `403 Forbidden`
- CUSTOMER can create payments
- ADMIN can monitor payments
- Prevent `/register` from allowing public ADMIN creation

---

## Stage 2 — Docker + CI/CD + Supply Chain

| Day | Goal |
|---|---|
| **Day 12** | Dockerize SecurePay using a production-oriented multi-stage build. |
| **Day 13** | Docker Compose: API + PostgreSQL + health checks. |
| **Day 14** | Externalized configuration and environment profiles. |
| **Day 15** | GitHub Actions: build + unit tests + integration tests. |
| **Day 16** | Static Analysis and quality gates. |
| **Day 17** | Dependency vulnerability scanning. |
| **Day 18** | Secret scanning and credential hygiene. |
| **Day 19** | Container image scanning and image hardening. |
| **Day 20** | Generate SBOM and analyze software supply-chain risks. |
| **Day 21** | Complete DevSecOps pipeline + PR/branch rules. |

---

## Stage 3 — OWASP Web & API Security

This stage's Day 22–32 sequence uses OWASP Top 10:2021 categories. OWASP has since published [Top 10:2025](https://top10.owasp.org/2025/0x00_2025-Introduction/), which changes the numbering and scope: for example, A06:2021 expands into Software Supply Chain Failures, SSRF is folded into Broken Access Control, and A10 becomes Mishandling of Exceptional Conditions. The documentary reviews retain the planned 2021 labels so their evidence stays traceable; refresh the curriculum mapping before using it as a current OWASP checklist.

| Day | Goal |
|---|---|
| **Day 22 ✅** | Map SecurePay against OWASP Top 10. [Mapping and verification](docs/day22-owasp-mapping.md). |
| **Day 23 ✅** | Broken Access Control. [Lesson and evidence](docs/day23-access-control.md). |
| **Day 24 ✅** | Cryptographic Failures. [Lesson and evidence](docs/day24-cryptographic-failures.md). |
| **Day 25** | Injection prevention + tests. |
| **Day 26** | Insecure Design and business-logic abuse cases. [Design review and decision points](docs/day26-insecure-design.md). |
| **Day 27** | Security Misconfiguration. [Documentary review](docs/day27-security-misconfiguration.md); runtime hardening remains open. |
| **Day 28** | Vulnerable & Outdated Components. [Documentary review](docs/day28-vulnerable-components.md); ongoing scan and patch-response practice remains open. |
| **Day 29** | Authentication Failures and token security. [Documentary review](docs/day29-authentication.md); production identity controls remain open. |
| **Day 30** | Software & Data Integrity Failures. [Documentary review](docs/day30-integrity.md); provider signature and release-integrity exercises remain open. |
| **Day 31** | Security Logging & Monitoring Failures. [Documentary review](docs/day31-logging-monitoring.md); telemetry and alert implementation remains open. |
| **Day 32** | SSRF and outbound-call controls. [Documentary review](docs/day32-ssrf.md); no outbound integration is present to test. |
| **Day 33** | Map SecurePay against OWASP API Security Top 10. [Documentary mapping](docs/day33-api-top10.md); several future controls remain open. |
| **Day 34 ✅** | Configurable in-memory rate limits for login and payment creation. [Implementation, test evidence and limits](docs/day34-rate-limiting.md). |
| **Day 35 ✅** | API negative/security test suite. [Implemented and verified on main](docs/day35-api-negative-tests.md). |

---

## Stage 4 — Observability & Reliability

| Day | Goal |
|---|---|
| **Day 36 ✅** | Structured logging + Correlation ID. [Implementation and evidence](docs/day36-structured-logging.md). |
| **Day 37 ✅** | Micrometer payment transition metrics, protected Actuator endpoint, and PostgreSQL-backed verification. [Implementation and evidence](docs/day37-metrics.md). |
| **Day 38 ✅** | OpenTelemetry fundamentals and manual payment-creation span. [Implementation and evidence](docs/day38-opentelemetry-fundamentals.md). |
| **Day 39 ✅** | Distributed tracing configuration and W3C context propagation for outgoing `RestClient` calls. [Implementation and evidence](docs/day39-distributed-tracing.md). |
| **Day 40 ✅** | Liveness stays independent of PostgreSQL; readiness and the Compose health check include PostgreSQL availability. [Implementation and evidence](docs/day40-health-probes.md). |
| **Day 41 ✅** | Propose payment API availability and latency indicators/objectives, document measurement definitions and identify telemetry gaps. [Definitions and limits](docs/day41-sli-slo.md). |
| **Day 42** | Actionable alerting. |
| **Day 43** | Simulate dependency failures. |
| **Day 44** | Timeout + Retry + Circuit Breaker + Bulkhead. |
| **Day 45** | Operational dashboard + incident-response documentation. |

---

## Stage 5 — Kubernetes

| Day | Goal |
|---|---|
| **Day 46** | `Deployment` + `Service`. |
| **Day 47** | `ConfigMap` + `Secret`. |
| **Day 48** | Probes + CPU/Memory requests & limits. |
| **Day 49** | Ingress + TLS. |
| **Day 50** | Horizontal Pod Autoscaler. |
| **Day 51** | `SecurityContext`, non-root and Linux capabilities. |
| **Day 52** | `NetworkPolicy`. |
| **Day 53** | Pod Security Standards / Admission Control. |
| **Day 54** | Kubernetes Secrets + external secret manager. |
| **Day 55** | Kubernetes Threat Model. |
| **Day 56** | Cluster Security & Availability Review. |

---

## Stage 6 — Cloud + Infrastructure as Code

| Day | Goal |
|---|---|
| **Day 57** | Design SecurePay cloud architecture. |
| **Day 58** | GCP: IAM, VPC, Cloud SQL, GKE/Cloud Run, Secret Manager. |
| **Day 59** | AWS: IAM, RDS, EKS/ECS, Secrets Manager. |
| **Day 60** | Azure: Identity, AKS, DB, Key Vault and networking. |
| **Day 61** | Terraform fundamentals. |
| **Day 62** | Networking with Terraform. |
| **Day 63** | Managed database with Terraform. |
| **Day 64** | Compute/runtime with Terraform. |
| **Day 65** | Remote State + Locking + state security. |
| **Day 66** | Terragrunt + `dev/staging/prod` environments. |
| **Day 67** | CloudFormation comparison exercise. |
| **Day 68** | IaC Security Scanning. |
| **Day 69** | Policy as Code. |
| **Day 70** | Full Cloud Security Review. |

At this point the review mindset should cover:

```text
IAM → Network → Secrets → Encryption → Logs → Backup → Availability
```

---

## Stage 7 — Architecture & Engineering Leadership

| Day | Goal |
|---|---|
| **Day 71** | Architecture diagram + trust boundaries. |
| **Day 72** | ADR for JWT / Authentication. |
| **Day 73** | ADR for Payment State + Idempotency. |
| **Day 74** | Write a real SecurePay RFC. |
| **Day 75** | Modular Monolith vs Microservices. |
| **Day 76** | Event-Driven Architecture for payments. |
| **Day 77** | Consistency vs availability vs resiliency. |

---

## Stage 8 — Loop Engineering

| Day | Goal |
|---|---|
| **Day 78** | Design the loop: `Signal → Issue → Code → Test → CI → Review → Merge`. |
| **Day 79** | Minimal Grafana/observability → Issue pilot. |
| **Day 80** | Measure engineering-loop lead time and identify improvements. |

Example:

```text
Grafana detects problem
        ↓
Create GitHub Issue
        ↓
Developer / AI analyzes it
        ↓
PR
        ↓
Tests
        ↓
Security checks
        ↓
Review
        ↓
Merge
        ↓
Deploy
        ↓
Observe again
```

---

## Stage 9 — AI / LLM Security

| Day | Goal |
|---|---|
| **Day 81** | OWASP LLM Top 10. |
| **Day 82** | Prompt Injection. |
| **Day 83** | Sensitive Information Disclosure. |
| **Day 84** | Excessive Agency + tool permissions. |
| **Day 85** | AI Supply Chain / Models / Dependencies. |
| **Day 86** | Design a secure AI-assisted operations use case for SecurePay. |

Example future flow:

```text
SecurePay AI Security Assistant

Grafana
   ↓
Logs / traces
   ↓
LLM
   ↓
Incident analysis
   ↓
Possible root cause
   ↓
GitHub Issue
```

Controls must include least privilege, sensitive-data handling and human approval where appropriate.

---

## Stage 10 — Technical Leadership + Portfolio

| Day | Goal |
|---|---|
| **Day 87** | Security prioritization, backlog and risk communication. |
| **Day 88** | Architecture Review simulation with CTO / business stakeholders. |
| **Day 89** | Turn SecurePay into a professional portfolio project. |
| **Day 90** | Final assessment + next 90-day roadmap. |

By Day 89, the repository should clearly expose something close to:

```text
SecurePay
├── architecture/
├── docs/
│   ├── adr/
│   └── rfc/
├── security/
│   ├── threat-models/
│   ├── risk-register.md
│   └── security-verification-plan.md
├── terraform/
├── kubernetes/
├── .github/workflows/
├── src/
├── docker-compose.yml
├── Dockerfile
├── README.md
└── ROADMAP.md
```

## 90-Day Goal

The objective is not just to learn Spring Security, Kubernetes or Terraform separately.

By the end of the roadmap, the project should support this statement:

> **I designed, developed, secured, deployed and observed this payment platform using DevSecOps and Cloud Security practices.**

And, more importantly, every significant technical and security decision should be explainable and defensible.

## Working Rules

- Prefer small, reviewable changes.
- Every security control should have a reason and, where possible, a test.
- Keep secrets out of source control.
- Record significant decisions in ADRs/RFCs.
- Treat observability signals as inputs to engineering work.
- Use SecurePay as both a learning project and a portfolio artifact.
- Do not skip days unless there is a documented reason.
