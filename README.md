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

Create a payment with an idempotency key:

```shell
curl -X POST http://localhost:8080/api/payments \
  -u customer-123:local-development-only \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: example-payment-001" \
  -d '{"merchantId":"merchant-456","amount":10.00,"currency":"USD"}'
```

The local API uses HTTP Basic authentication for development only. Payments are always created for the authenticated customer; production must use an external identity provider and must not use the configured demo password.

Payment and idempotency data are stored through JPA and managed by Flyway migrations. The default local/test profile uses an in-memory database; the `prod` profile requires PostgreSQL connection values through `SECUREPAY_DATABASE_URL`, `SECUREPAY_DATABASE_USERNAME`, and `SECUREPAY_DATABASE_PASSWORD`.

The health endpoint is available at `http://localhost:8080/actuator/health`. It reports the overall application and database status without exposing component details. Readiness and liveness probes are available at `/actuator/health/readiness` and `/actuator/health/liveness`.

Interactive API documentation is available locally at `http://localhost:8080/swagger-ui.html`; the OpenAPI document is at `/v3/api-docs`. Swagger UI is disabled in the `prod` profile. In the UI, use **Authorize** and enter the local demo credentials to try the protected payment endpoints.

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
