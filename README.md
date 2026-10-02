# SecurePay

SecurePay is a secure payment platform built as a DevSecOps and Cloud Security learning project.

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

SecurePay requires Java 25 and Maven. For the default in-memory database, start the API with:

```shell
mvn spring-boot:run
```

Run the test suite with:

```shell
mvn test
```

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

Payment and idempotency data are stored through JPA and managed by Flyway migrations. The default local/test profile uses an in-memory database; the `prod` profile requires PostgreSQL connection values through `SECUREPAY_DATABASE_URL`, `SECUREPAY_DATABASE_USERNAME`, and `SECUREPAY_DATABASE_PASSWORD`.

The health endpoint is available at `http://localhost:8080/actuator/health`. It reports the overall application and database status without exposing component details. Readiness and liveness probes are available at `/actuator/health/readiness` and `/actuator/health/liveness`.

Interactive API documentation is available locally at `http://localhost:8080/swagger-ui.html`; the OpenAPI document is at `/v3/api-docs`. Swagger UI is disabled in the `prod` profile. In the UI, call `/auth/login`, copy the returned JWT, then use **Authorize** with the Bearer token to try protected endpoints.

### Run the API and PostgreSQL with Docker

With Docker Desktop running, start both services from PowerShell:

```powershell
.\scripts\run-local.ps1
```

The script builds the Java 25 API image, starts PostgreSQL, waits for the API readiness check, and prints the local URLs. Stop the containers with `docker compose down`. To use a different API port when port 8080 is already occupied, set `SECUREPAY_API_PORT` before running the script. You can also override the local-only database or demo passwords with `SECUREPAY_DATABASE_PASSWORD` or `SECUREPAY_DEMO_PASSWORD`.

### Run against PostgreSQL

Docker Compose starts a local PostgreSQL database with a persistent volume. Start it with:

```shell
docker compose up -d postgres
```

Then run SecurePay with the `postgres` profile so Flyway applies the migrations to PostgreSQL:

```shell
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

The local database is available only on `127.0.0.1:5432`. Its default password is for local development only; set `SECUREPAY_DATABASE_PASSWORD` before starting Compose to override it. Stop the database with `docker compose down`; its data remains in the Compose volume.

## Security

SecurePay will follow security practices based on:

- OWASP Top 10
- OWASP API Security Top 10
- Cloud Security best practices

Security planning artifacts:

- [Payment flow threat model](security/threat-models/securepay-payment-flow.md)
- [Security risk register](security/risk-register.md)
- [Security verification plan](security/security-verification-plan.md)
