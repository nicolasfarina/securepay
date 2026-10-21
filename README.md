# SecurePay

SecurePay is a secure payment platform built as a DevSecOps and Cloud Security learning project.

## Learning progress — Days 23 and 24 complete

On **October 21, 2026**, the learner completed guided lessons in Spanish on access control and cryptographic failures, using existing tests. See [Day 23: access rules, matrix and evidence](docs/day23-access-control.md) and [Day 24: cryptography, test results and limits](docs/day24-cryptographic-failures.md).

- Day 23: ownership HTTP test passed (pasted log: 1 test, no failures/errors/skips); the learner also reported the customer/admin role test passed.
- Day 24: the learner reported the JWT payload test passed and supplied the invalid-token summary: **6 tests, 0 failures, 0 errors, 0 skipped**.
- Covered roles vs. ownership, server-side checks, BCrypt hashes, readable signed JWTs, signing secrets, HTTPS and encryption at rest.
- Pending work remains explicit: missing access-matrix tests, key rotation, deployment TLS and storage/backup encryption. These learning milestones do not certify production security.
- Application and test code were unchanged. Evidence reported by the learner is distinguished from assistant and CI verification.

**Next session: Day 25 — Injection prevention and tests, planned for October 22, 2026.** Continue in Spanish, step by step; review validation and database queries, then agree any code changes before implementing them.

## Learning progress — Day 22 complete

On **October 21, 2026**, Day 22's initial OWASP mapping and practical authorization verification were completed. See the [full map, evidence, English practice and next-session plan](docs/day22-owasp-mapping.md).

- Mapped all ten OWASP Top 10:2021 categories used by the roadmap to existing controls, source/test evidence and remaining work; included a cross-reference to the 2025 edition.
- Reviewed authentication vs. authorization: missing/invalid authentication → **401**; insufficient role → **403**; another customer's payment → **404** to hide its existence; the owner → **200**.
- Executed the real HTTP ownership test successfully, followed by **51 related integration tests: 0 failures, 0 errors, 0 skipped**, using Java 25 and H2. These checks were run by the assistant; PostgreSQL and operational security were not reverified in this session.
- Recorded open work including deployment cryptography, login rate limiting, security audit events/alerts and software supply-chain integrity. This is an initial security map, not a claim that every risk is resolved.

Day 22 prepared the access-control lesson subsequently completed on Day 23; see the current progress above. Application and test code were unchanged for Day 22.

## Goal

The goal of SecurePay is to create a secure, observable, and scalable payment platform using modern software engineering practices.

## Main Features

- Credit card payments
- Secure payment processing
- Payment status management
- Idempotent payment requests
- Authentication and authorization
- Observability
- Cloud infrastructure
- DevSecOps security controls

## Payment Status

A payment can have the following status:

- `PENDING` — The payment is being processed.
- `APPROVED` — The payment was completed successfully.
- `REJECTED` — The payment was not approved.
- `UNKNOWN` — The final result is not yet known.
- `REFUNDED` — The approved payment was returned.

## Technologies

- Java
- Spring Boot
- PostgreSQL
- Docker
- Kubernetes
- Terraform
- Terragrunt
- Google Cloud Platform
- OpenTelemetry
- GitHub Actions

## Run locally

SecurePay requires Java 25 and Maven. The default `local` profile uses an in-memory H2 database and local-only demo credentials. Start the API with:

```shell
mvn spring-boot:run
```

Run the test suite with:

```shell
mvn test
```

For Day 11 verification with a disposable PostgreSQL database, run from PowerShell with Docker Desktop running:

```powershell
.\scripts\test-integration.ps1
```

This runs Maven verification, including the real HTTP API suite and PostgreSQL concurrency tests, then removes its own test database container. It uses temporary loopback ports and restores the process environment, so an existing local API/database can stay running. Requires Java 25, Maven and Docker. See [coverage, results and limitations](docs/day11-integration-verification.md). The default `mvn test` uses H2 and explicitly skips the five PostgreSQL-only tests.

Authenticate and create a payment with an idempotency key:

```shell
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"customer-123","password":"local-development-only"}'
```

Use the returned `accessToken` as a Bearer token:

```shell
curl -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: example-payment-001" \
  -d '{"merchantId":"merchant-456","amount":10.00,"currency":"USD"}'
```

Local demo users are `customer-123`, `customer-789`, and `admin-123`. Passwords are BCrypt-encoded in memory and JWTs are signed with HS256. The development signing key is local-only; production requires `SECUREPAY_JWT_SECRET` and should ultimately use an external identity provider.

Payment and idempotency data are stored through JPA and managed by Flyway migrations. Configuration is selected with Spring profiles:

| Profile | Database | Credentials and intended use |
|---|---|---|
| `local` (default) | In-memory H2 | Local learning defaults. Set `SECUREPAY_DEMO_PASSWORD` and `SECUREPAY_JWT_SECRET` to override demo credentials. |
| `postgres` | PostgreSQL | Local PostgreSQL development. Database URL/user default to `localhost:5432/securepay`; set `SECUREPAY_DATABASE_PASSWORD` and `SECUREPAY_JWT_SECRET`. |
| `prod` | PostgreSQL | Deployment profile. Requires `SECUREPAY_DATABASE_URL`, `SECUREPAY_DATABASE_USERNAME`, `SECUREPAY_DATABASE_PASSWORD`, `SECUREPAY_DEMO_PASSWORD`, and `SECUREPAY_JWT_SECRET`; Swagger is disabled. |

For example, run against a local PostgreSQL instance with PowerShell:

```powershell
$env:SECUREPAY_DATABASE_PASSWORD = 'your-local-database-password'
$env:SECUREPAY_JWT_SECRET = 'replace-with-a-random-secret-at-least-32-characters'
mvn spring-boot:run '-Dspring-boot.run.profiles=postgres'
```

Set environment values in the deployment platform's secret manager for production; do not commit them to source control. Compose requires explicit `SECUREPAY_DATABASE_PASSWORD`, `SECUREPAY_DEMO_PASSWORD`, and `SECUREPAY_JWT_SECRET` values and fails during configuration if any are missing.

The health endpoint is available at `http://localhost:8080/actuator/health`. It reports the overall application and database status without exposing component details. Readiness and liveness probes are available at `/actuator/health/readiness` and `/actuator/health/liveness`.

Interactive API documentation is available locally at `http://localhost:8080/swagger-ui.html`; the OpenAPI document is at `/v3/api-docs`. Swagger UI is disabled in the `prod` profile. In the UI, call `/auth/login`, copy the returned JWT, then use **Authorize** with the Bearer token to try protected endpoints.

### Run the API and PostgreSQL with Docker

The Dockerfile builds and tests the application with Maven on JDK 26, targeting Java 25, then copies its executable JAR into a separate Java 25 runtime image running as UID/GID 10001. Base images are pinned by digest. The final image has no Maven, compiler or project source tree.

Build the image and verify it against an isolated, disposable PostgreSQL container (PowerShell 7 and Docker Desktop required; host Java/Maven are not needed):

```powershell
.\scripts\test-container.ps1
```

The script checks the production profile, authentication, payment retries, validation, database row counts, a read-only root filesystem and SIGTERM shutdown, then cleans up its own containers/network/volume. It preserves existing local sessions. See [Day 12 details and results](docs/day12-container-verification.md).

With Docker Desktop running, start both services from PowerShell:

```powershell
.\scripts\run-local.ps1
```

The Compose stack uses PostgreSQL 18. Its data directory and volume location differ from PostgreSQL 17. If you already have a PostgreSQL 17 Compose volume, do not start it with the new image expecting an automatic upgrade; back it up and follow [the PostgreSQL 18 volume and migration notes](docs/postgres-18-volume-migration.md) first. The verification script uses a separate disposable volume.

The script builds the Java 25 API image and waits until both PostgreSQL and the API are healthy. The API's readiness includes database access; its liveness does not depend on the database. PostgreSQL is reachable by the API as `postgres:5432` on the Compose network and has no host port by default. The API runs with a read-only root filesystem and a writable `/tmp`.

For a separate local demo on another API port:

```powershell
.\scripts\run-local.ps1 -ApiPort 8082 -ProjectName securepay-day13-demo
docker compose --project-name securepay-day13-demo ps
docker compose --project-name securepay-day13-demo down
```

`down` stops/removes the containers and network while retaining the named PostgreSQL volume. Reusing the same project name reuses its data. `down --volumes` deletes that data; it is only used by the isolated verification script. Volumes are not backups. Set `SECUREPAY_DATABASE_PASSWORD`, `SECUREPAY_DEMO_PASSWORD`, and `SECUREPAY_JWT_SECRET` before first startup. Changing the database password environment value alone does not change an already initialized PostgreSQL user's password.

Run the isolated Day 13 checks with:

```powershell
.\scripts\test-compose.ps1
```

This verifies startup health, payment persistence across API and full stack replacement, readiness/liveness during a database outage, and recovery. It deletes only its own disposable verification project's data. See [Day 13 evidence and lesson](docs/day13-compose-verification.md).

### Run against PostgreSQL

To run Java or a database client on the host, opt into publishing PostgreSQL on loopback with the optional override:

```shell
docker compose -f compose.yaml -f compose.host-db.yaml up -d --wait postgres
```

Then run SecurePay with the `postgres` profile so Flyway applies the migrations to PostgreSQL:

```shell
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

With that override, the database is available on `127.0.0.1:5432`; override the published port with `SECUREPAY_POSTGRES_PORT` and adjust the host application's database URL accordingly. Its default password is for local development only. Stop it with `docker compose -f compose.yaml -f compose.host-db.yaml down`; its data remains in the Compose volume.

## Security

SecurePay will follow security practices based on:

- OWASP Top 10
- OWASP API Security Top 10
- Cloud Security best practices

Security planning artifacts:

- [Payment flow threat model](security/threat-models/securepay-payment-flow.md)
- [Security risk register](security/risk-register.md)
- [Security verification plan](security/security-verification-plan.md)

Keep local credentials in process environment variables or an ignored `.env` file; do not commit them. CI runs Gitleaks against pull requests, pushes to `main` and `codex/*`, and the repository's full Git history on a weekly schedule. If a real secret is found, revoke or rotate it first; deleting it from the latest file does not invalidate copies in Git history.
