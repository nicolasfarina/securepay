# Day 11 — API integration verification

Implemented and verified locally on October 16, 2026. This branch depends on Day 10 (`feat/day10-idempotency`). Review and merge Day 10 first, then review Day 11; neither branch is merged automatically.

## Scope

`ApiEndToEndTest` starts the root SecurePay application on a random loopback port and sends real HTTP requests using Java's HTTP client. Tests exercise Tomcat, JWT login and filters, method authorization, MVC validation, error dispatch, Flyway and JPA. No authentication or persistence behavior is mocked in this class.

The default run uses a separate in-memory H2 database. With `SECUREPAY_POSTGRES_TEST=true`, the HTTP suite and the five Day 10 concurrency/rollback cases use explicitly configured PostgreSQL. The existing 15 tests retain their H2 configuration. All tests run sequentially under the current Maven configuration; tests delete payment and idempotency rows between cases and must use a disposable database.

| Coverage | Assertions |
| --- | --- |
| Complete payment flow | Login, create, equivalent replay, retrieve by owner and list by admin; consistent response fields, one persisted payment and one idempotency record |
| Customer ownership | Other customer receives 404, owner retains access, missing payment returns 404 |
| Conflicting retry | HTTP 409 with Problem Details; original amount and payment count preserved |
| Payment validation | Twelve cases covering blank/oversized merchant, null/zero/negative/below-minimum amount, scale/precision limits, currency format, missing fields and malformed JSON; HTTP 400 and no writes |
| Idempotency header | Missing, empty, whitespace and oversized keys return HTTP 400 without writes; maximum key/merchant lengths are accepted |
| Path validation | Invalid UUID returns 400; absent UUID returns 404 |
| Permissions | Anonymous requests return 401, customer cannot list admin payments, admin cannot create customer payments (403), denied requests do not write rows |
| JWT rejection | Malformed/empty/expired token, wrong signing key, customer token claiming ADMIN and unknown subject all return 401 for payment creation and admin access |
| Login failures | Unknown/wrong credentials return 401 without token/secret leakage; invalid/malformed login data returns 400 |
| Request isolation | An authenticated admin request does not authenticate the next request; direct anonymous `/error` access remains protected |
| Public operational routes | Health/readiness/liveness are UP without component details, and OpenAPI advertises Bearer authentication |

The five PostgreSQL tests retained from Day 10 force concurrent equivalent/conflicting requests and check sequential retries, customer isolation and rollback after an unrelated integrity error. Database configuration is shared in `PostgresTestDatabase` to preserve explicit opt-in and prevent an accidental fallback to H2.

## Defects reproduced and fixed

Before changing application code, the 36 new HTTP cases ran against H2: **22 failed, 14 passed**. These failures exposed:

1. **Error dispatch changed response codes.** Spring Security attempted to authenticate the servlet ERROR dispatch, replacing some intended 400/403 responses with 401. Permit only `DispatcherType.ERROR` before the usual route rules; ordinary requests to `/error` still require authentication.
2. **Input limits were not enforced at the API boundary.** Match merchant ID/key validation to the database's 128/255 character limits. Use MVC's built-in method validation instead of controller-level `@Validated`, allowing header violations to return 400. Invalid input does not reach persistence.
3. **Payment timestamps changed between first response and replay.** Database timestamp precision is microseconds, while the initial object used nanoseconds. Truncate creation timestamps to microseconds so the initial response, stored payment and replay agree. No schema migration is required.

## Results

Environment: Windows PowerShell 7, Java 25.0.4.1, Maven 3.9.16, PostgreSQL 17.11 (Docker image `postgres:17`).

| Run | Executed | Failures/errors | Skipped | Build |
| --- | --- | --- | --- | --- |
| Before fixes: new HTTP suite on H2 | 36 | 22 failures / 0 errors | 0 | Failed as expected |
| After fixes: default Maven verify | 51 | 0 / 0 | 5 PostgreSQL-only cases | Passed |
| After fixes: integration script with PostgreSQL | 56 | 0 / 0 | 0 | Passed |

Both successful verification runs built the executable application JAR. Logs are local and ignored under `target/day11-evidence/`: `before.log`, `h2-verify.log`, `postgres-verify.log` and `local-run.log`. Surefire XML/text results are under `target/surefire-reports/`.

## Reproduce

From the repository root with Java 25, Maven and Docker Desktop available:

```powershell
# Fast local verification: H2 HTTP suite; five PostgreSQL cases are skipped.
mvn -B '-Duser.timezone=UTC' verify

# Full Day 11 verification: all 56 cases, including PostgreSQL HTTP/concurrency tests.
.\scripts\test-integration.ps1
```

The script creates a uniquely named PostgreSQL container with a generated disposable password and random loopback port, waits for TCP readiness, sets the explicit test environment, runs Maven verification and removes its container/volume in `finally`. Prior process environment values are restored. An existing Swagger/API session and its database can stay running. The script does not publish images or deploy anything.

`PostgresTestDatabase` requires a URL ending in `/securepay_test` without query parameters and explicit username/password. Never point the tests at a database whose data must be preserved. UTC avoids the Java/PostgreSQL timezone-alias issue observed on this machine.

## Limits and next milestone

Coverage applies to the executable root application and current routes, not the separate service foundation modules, provider processing, restart durability, production security certification or performance/load testing. Day 15 still owns CI workflow work; this change adds no new automatic merge/deployment behavior.

Day 11 implementation and local verification are complete. Manual review and merge remain pending. Day 12 is the planned Docker packaging milestone.
