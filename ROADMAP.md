# SecurePay — 90 Day DevSecOps & Cloud Security Roadmap

**Current focus:** Day 9 — Authentication & Authorization with JWT.

## Days 1–11 — Application Foundations
- **Day 1:** Payment flow, states (`PENDING`, `APPROVED`, `REJECTED`, `UNKNOWN`, `REFUNDED`), retries and idempotency.
- **Day 2:** Create repository, bootstrap project, README, first commit.
- **Day 3:** Payment domain, validations, service layer and unit tests.
- **Day 4:** REST API, request/response contracts, HTTP status strategy and API docs.
- **Day 5:** Persistence with JPA, PostgreSQL and Flyway.
- **Day 6:** Centralized error handling and standardized API errors.
- **Day 7:** Threat modeling for the payment flow and initial risk register.
- **Day 8:** Baseline security controls, roles and password-security requirements.
- **Day 9:** JWT authentication and authorization: `User`, `UserRole`, `CUSTOMER`, `ADMIN`, BCrypt, `POST /auth/login`, Bearer token validation, Spring Security, `401`/`403`, role-protected endpoints, and no public self-registration as ADMIN.
- **Day 10:** Payment flow + idempotency, duplicate prevention and retry behavior.
- **Day 11:** Integration tests for auth, payment, persistence, idempotency and multiple origins/clients.

## Days 12–21 — Containers, CI/CD & Supply Chain
- **Day 12:** Dockerize SecurePay with a production-oriented multi-stage image.
- **Day 13:** Docker Compose for API + PostgreSQL + health checks.
- **Day 14:** Externalized configuration and environment profiles.
- **Day 15:** GitHub Actions CI: build, unit tests and integration tests.
- **Day 16:** Static analysis and code-quality gates.
- **Day 17:** Dependency vulnerability scanning.
- **Day 18:** Secret scanning and credential hygiene.
- **Day 19:** Container image scanning and hardened base images.
- **Day 20:** Generate SBOM and review software supply-chain risks.
- **Day 21:** Consolidate secure CI/CD workflow and branch/PR rules.

## Days 22–35 — OWASP Web & API Security
- **Day 22:** Map SecurePay against OWASP Top 10.
- **Day 23:** Broken Access Control testing.
- **Day 24:** Cryptographic Failures review.
- **Day 25:** Injection prevention and tests.
- **Day 26:** Insecure Design and business-logic abuse cases.
- **Day 27:** Security Misconfiguration review.
- **Day 28:** Vulnerable and Outdated Components policy.
- **Day 29:** Authentication Failures, token expiration and invalid-token handling.
- **Day 30:** Software and Data Integrity Failures.
- **Day 31:** Security Logging and Monitoring Failures.
- **Day 32:** SSRF and outbound-call controls.
- **Day 33:** OWASP API Security Top 10 mapping.
- **Day 34:** Rate limiting for login and payment endpoints.
- **Day 35:** API abuse and negative security test suite.

## Days 36–45 — Observability & Reliability
- **Day 36:** Structured logging and correlation IDs.
- **Day 37:** Metrics with Micrometer.
- **Day 38:** OpenTelemetry fundamentals.
- **Day 39:** Distributed tracing.
- **Day 40:** Liveness and readiness health strategy.
- **Day 41:** Define SLIs and SLOs.
- **Day 42:** Actionable alerting.
- **Day 43:** Failure injection for dependencies.
- **Day 44:** Timeouts, retries, circuit breakers and bulkheads.
- **Day 45:** Operational dashboard and incident-response notes.

## Days 46–56 — Kubernetes
- **Day 46:** Deployment and Service manifests.
- **Day 47:** ConfigMaps and Secrets.
- **Day 48:** Probes plus CPU/memory requests and limits.
- **Day 49:** Ingress and TLS termination.
- **Day 50:** Horizontal Pod Autoscaling.
- **Day 51:** SecurityContext, non-root and capability reduction.
- **Day 52:** NetworkPolicies.
- **Day 53:** Pod Security Standards and admission-control concepts.
- **Day 54:** Kubernetes secrets strategy and external secret managers.
- **Day 55:** Kubernetes threat model.
- **Day 56:** Kubernetes security and availability review.

## Days 57–70 — Cloud & Infrastructure as Code
- **Day 57:** SecurePay cloud architecture.
- **Day 58:** GCP mapping: IAM, VPC, Cloud SQL, GKE/Cloud Run, Secret Manager.
- **Day 59:** AWS mapping: IAM, RDS, EKS/ECS, Secrets Manager.
- **Day 60:** Azure mapping: identity, AKS, database, Key Vault and networking.
- **Day 61:** Terraform fundamentals.
- **Day 62:** Terraform networking.
- **Day 63:** Terraform managed database.
- **Day 64:** Terraform compute/runtime.
- **Day 65:** Remote state, locking and state security.
- **Day 66:** Terragrunt and multi-environment structure.
- **Day 67:** CloudFormation comparison exercise.
- **Day 68:** IaC security scanning.
- **Day 69:** Policy as Code.
- **Day 70:** Cloud security review: least privilege, encryption, secrets, logs and backups.

## Days 71–80 — Architecture, RFCs & Loop Engineering
- **Day 71:** Current-state architecture diagram with trust boundaries.
- **Day 72:** ADR for JWT/authentication.
- **Day 73:** ADR for payment states and idempotency.
- **Day 74:** RFC for a significant SecurePay change.
- **Day 75:** Evaluate modular monolith vs microservices boundaries.
- **Day 76:** Event-driven architecture concepts for payment events.
- **Day 77:** Resilience and consistency trade-offs.
- **Day 78:** Introduce Loop Engineering: signal → issue → change → tests → CI → review → merge.
- **Day 79:** Build a minimal Grafana/observability signal-to-issue pilot.
- **Day 80:** Measure engineering-loop lead time and define improvements.

## Days 81–86 — AI & LLM Security
- **Day 81:** OWASP LLM Top 10 overview.
- **Day 82:** Prompt injection and trust-boundary exercises.
- **Day 83:** Sensitive-information disclosure and data handling.
- **Day 84:** Excessive agency and tool-permission controls.
- **Day 85:** Supply-chain and model/dependency risks for AI features.
- **Day 86:** Design a secure AI-assisted operations use case for SecurePay.

## Days 87–90 — Leadership, Portfolio & Final Review
- **Day 87:** Technical leadership: security prioritization, backlog and risk communication.
- **Day 88:** Architecture review simulation with CTO/business stakeholders.
- **Day 89:** Portfolio hardening: README, diagrams, ADRs, threat models, CI evidence and screenshots.
- **Day 90:** Final SecurePay assessment: architecture, security gaps, lessons learned and next 90-day plan.

---

## Working Rules
- Prefer small, reviewable changes.
- Every security control should have a reason and, where possible, a test.
- Keep secrets out of source control.
- Record significant decisions in ADRs/RFCs.
- Treat observability signals as inputs to engineering work.
- Use SecurePay as both a learning project and a portfolio artifact.
