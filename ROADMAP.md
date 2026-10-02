# SecurePay — 90 Day DevSecOps & Cloud Security Roadmap

**Current focus:** Day 10 — Payment idempotency and duplicate prevention.

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

| Day | Goal |
|---|---|
| **Day 22** | Map SecurePay against OWASP Top 10. |
| **Day 23** | Broken Access Control. |
| **Day 24** | Cryptographic Failures. |
| **Day 25** | Injection prevention + tests. |
| **Day 26** | Insecure Design and business-logic abuse cases. |
| **Day 27** | Security Misconfiguration. |
| **Day 28** | Vulnerable & Outdated Components. |
| **Day 29** | Authentication Failures and token security. |
| **Day 30** | Software & Data Integrity Failures. |
| **Day 31** | Security Logging & Monitoring Failures. |
| **Day 32** | SSRF and outbound-call controls. |
| **Day 33** | Map SecurePay against OWASP API Security Top 10. |
| **Day 34** | Rate limiting for login and payment endpoints. |
| **Day 35** | API negative/security test suite. |

---

## Stage 4 — Observability & Reliability

| Day | Goal |
|---|---|
| **Day 36** | Structured logging + Correlation ID. |
| **Day 37** | Metrics with Micrometer. |
| **Day 38** | OpenTelemetry fundamentals. |
| **Day 39** | Distributed Tracing. |
| **Day 40** | Proper liveness/readiness strategy. |
| **Day 41** | Define SLIs and SLOs. |
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
