# Day 25 — Injection prevention and tests

Date: 2026-10-22 (America/Buenos_Aires).

Reviewed revision: `main` at `4f1bca825f8ba2bf210a18397b9f321eb1f2a7f6`; day 25 tests and evidence are proposed in the linked pull request. No changes are merged into `main` by this session.

## Scope and conclusion

The root application accepts login credentials, payment fields, an idempotency key and a UUID path parameter. Its persistence layer uses standard Spring Data JPA repository methods. The source inventory found no hand-built SQL/JPQL, `@Query`, `createQuery`, `createNativeQuery`, `JdbcTemplate`, JDBC `Statement`, or dynamic sort/query input in `src/main`.

No SQL injection path was identified in the reviewed code. This is a source review plus regression testing of selected requests; it is not a penetration test and does not prove the entire application or future query code is safe. Spring Data JPA handles query values separately from query structure. Validation enforces field rules and complements that separation; it is not a substitute for parameterized persistence.

OWASP Top 10:2021 A03 Injection corresponds to A05 Injection in OWASP Top 10:2025. The roadmap follows the 2021 identifiers; see the [Day 22 mapping](day22-owasp-mapping.md).

## Input and query inventory

| Input | Current handling | Evidence and decision |
|---|---|---|
| Login username/password | `@NotBlank`; authentication uses the in-memory Spring Security user service. | SQL-looking username and password values do not authenticate in the Day 25 HTTP test. Character allowlists and size limits are not imposed: accepted username/password policy and compatibility need a separate decision. |
| `merchantId` | Nonblank, at most 128 characters. | Apostrophes and SQL-looking strings are stored and returned literally. No custom query consumes this field. A character allowlist would change the business contract, which is not defined here. |
| Amount and currency | Decimal constraints and a three-uppercase-letter currency pattern. | SQL-looking amount and currency values are rejected with HTTP 400 and create no rows. |
| `Idempotency-Key` | Nonblank, at most 255 characters. | A SQL-looking key is treated as a value; replay returns the same payment without another row. A stricter alphabet/minimum length would change the existing contract. |
| Payment path UUID | Spring converts the path value to `UUID`. | An encoded SQL-looking non-UUID path returns HTTP 400. |
| Persistence | `PaymentRepository` uses standard `JpaRepository` methods; `IdempotencyRecordRepository` uses a derived finder. | No user input is concatenated into SQL/JPQL in the reviewed root application source. Schema migrations are static. |

Useful review commands from the repository root:

```shell
rg -n '(@Query|createQuery|createNativeQuery|JdbcTemplate|Statement|PreparedStatement|EntityManager|Sort)' src/main
rg -n '(SELECT|INSERT|UPDATE|DELETE|select|insert|update|delete)' src/main/java src/main/resources/db/migration
```

The first search found no dynamic-query API in `src/main`. Static migration DDL is expected and is not constructed from request data. Review any future native queries, dynamic sorting or filtering for bound parameters and allowlisted query structure. Maven `verify` runs SpotBugs with the repository's SECURITY-category filter; the reviewed local run reported zero findings. This does not replace manual review of new query code.

## Executed tests

`Day25MerchantLiteralTest` exercises the running HTTP application and a temporary database. It covers:

- `O'Reilly` and strings resembling `OR`, `DROP TABLE` and `UNION SELECT` in `merchantId`; each is preserved literally on creation, database read and API retrieval.
- SQL-looking username and password inputs; both receive HTTP 401.
- Invalid SQL-looking amount and currency values; both receive HTTP 400 without writes.
- A SQL-looking idempotency key; equivalent retry returns the original payment and does not create another record.
- An encoded SQL-looking payment UUID; it receives HTTP 400.
- A different customer cannot retrieve a private payment (HTTP 404); other rows and counts stay unchanged.

On 2026-10-22, `mvn -B -ntp -Duser.timezone=UTC verify` completed successfully on Windows with Temurin Java 25.0.4.1, Maven 3.9.16 and an isolated H2 in-memory database: **64 cases counted, 0 failures, 0 errors, 5 PostgreSQL-only cases skipped; SpotBugs reported 0 findings**. The 5 skipped cases are existing PostgreSQL-specific tests.

The PostgreSQL-backed CI selection was also run locally against a new disposable PostgreSQL 18.6 container, with no existing SecurePay containers or data reused: `mvn -B -ntp -Duser.timezone=UTC -Dtest=ApiEndToEndTest,PaymentIdempotencyPostgresTest,Day25MerchantLiteralTest verify` completed with **49 tests, 0 failures, 0 errors, 0 skipped; SpotBugs reported 0 findings**. The disposable container was stopped after the test.

The Day 25 test is included in both the normal H2 `mvn clean verify` CI run and the PostgreSQL integration command in `.github/workflows/ci.yml`. Local H2 and PostgreSQL verification passed. GitHub Actions results remain pending on the pull request.

## Boundaries and follow-up

- HTTP client libraries reject some CRLF-containing header values before sending a request. That client-side rejection is not evidence of server-side validation; no backend CRLF claim is made here.
- The root application currently has no application logger. A sanitizing log helper without a log consumer would add unused code. Safe structured logging belongs with the Day 31 logging work.
- Uniform 400 ProblemDetail responses are an API consistency improvement, not evidence of an SQL injection. Existing Spring 400 handling is retained to avoid changing response contracts in this lesson.
- No character allowlist was imposed on merchant IDs, credentials or idempotency keys because their accepted formats are not specified. Agree those contracts before restricting values.
- The root project's successful Maven build does not execute the standalone `services/auth-service` and `services/payment-service` test suites as Maven modules; they are outside this root API query inventory.
- PostgreSQL tests and PR CI remain to be confirmed for the proposed branch. Production databases, provider calls, a frontend renderer, logging sinks and deployment configuration were not dynamically assessed.

No production application logic or API validation rules changed for this lesson. Changes are limited to focused tests, evidence, the OWASP mapping and CI test selection.
