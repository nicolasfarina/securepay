# Day 10 — Concurrent payment idempotency

Implementation and local verification completed on October 16, 2026. Manual review and merge are pending.

## Transaction decision

`PaymentService.create()` coordinates creation without holding a database transaction. The separate Spring bean `PaymentCreationTransaction` inserts the payment and idempotency record atomically in a `REQUIRES_NEW` transaction. The existing `(customer_id, idempotency_key)` unique constraint chooses the winner.

A losing creation rolls back completely before the service catches `DataIntegrityViolationException`. Recovery reads the winner in another `REQUIRES_NEW`, read-only transaction, compares its fingerprint and returns its payment. Different data produces `IdempotencyConflictException` (HTTP 409). If there is no matching record, the original integrity error is rethrown.

`REQUIRES_NEW` also isolates these operations if a future caller has an ambient transaction: payment creation commits independently of that caller. Callers must not expect a surrounding transaction to roll back a successfully created payment. There is no provider/network side effect in this transaction.

JWT authentication, customer ownership, fingerprint normalization, database constraints and the existing HTTP 201 replay contract remain in place.

## Verification

Environment: Java 25.0.4.1, Maven 3.9.16, PostgreSQL 17.11 in a disposable Docker container. Tests use MockMvc through the actual JWT login and payment endpoints, with real PostgreSQL persistence.

The two concurrency tests wrap the real repository lookup with a barrier. Every contender must read an absent key before any contender can insert. This forces the unique-constraint recovery path rather than relying on thread scheduling. A bounded executor and barrier timeout prevent indefinite waits.

| Scenario | Verified outcome |
| --- | --- |
| Eight concurrent requests, same customer/key/data | Eight HTTP 201 responses, one ID, one payment and one idempotency record |
| Two concurrent requests, same customer/key, different amounts | One HTTP 201 and one HTTP 409; one payment and one record |
| Sequential retry with `10.00` then `10.0` | Same payment ID; a later different amount returns HTTP 409 |
| Different customers with the same key | Two independent payment IDs and records |
| Unrelated integrity error (merchant exceeds database limit) | Original exception propagated; zero payment/idempotency rows |

Before the fix, the forced concurrent scenarios failed with a Hibernate assertion when recovery tried to reuse the failed persistence context. The unrelated-integrity-error case also lost the original exception. Both sequential scenarios passed (five PostgreSQL tests: one failure, two errors). After the fix, `mvn -B -Duser.timezone=UTC verify` builds the application and passes the complete suite: **20 tests, 0 failures, 0 errors, 0 skipped** (15 existing tests plus five PostgreSQL tests).

Local logs are under ignored `target/day10-evidence/`: `baseline.log`, `before.log`, `after.log`. Surefire XML/text reports are under `target/surefire-reports/`. These measurements cover local database payment creation, not an external payment provider or production deployment.

## Reproduce on Windows / PowerShell

Requires Java 25, Maven and a running Docker engine. Use only a disposable database: the PostgreSQL test clears payment and idempotency tables before each test. The test requires explicit opt-in and a database URL ending in `/securepay_test`; missing configuration fails rather than falling back to H2.

```powershell
docker run --detach --name securepay-day10-postgres `
  -e POSTGRES_DB=securepay_test -e POSTGRES_USER=securepay_test `
  -e POSTGRES_PASSWORD=day10-disposable-local `
  -p 127.0.0.1:55432:5432 postgres:17

# Wait for "accepting connections" before running Maven.
docker exec securepay-day10-postgres pg_isready -U securepay_test -d securepay_test

$env:SECUREPAY_POSTGRES_TEST = 'true'
$env:SECUREPAY_TEST_POSTGRES_URL = 'jdbc:postgresql://127.0.0.1:55432/securepay_test'
$env:SECUREPAY_TEST_POSTGRES_USERNAME = 'securepay_test'
$env:SECUREPAY_TEST_POSTGRES_PASSWORD = 'day10-disposable-local'
mvn -B '-Duser.timezone=UTC' verify

# After saving any desired test evidence:
docker rm --force --volumes securepay-day10-postgres
Remove-Item Env:SECUREPAY_POSTGRES_TEST, Env:SECUREPAY_TEST_POSTGRES_URL,
  Env:SECUREPAY_TEST_POSTGRES_USERNAME, Env:SECUREPAY_TEST_POSTGRES_PASSWORD
```

The password above is a disposable, loopback-only test credential. UTC avoids JDBC startup failures on systems whose Java timezone alias is absent from PostgreSQL's timezone data. Normal `mvn test` without the opt-in runs the existing suite and explicitly skips the five PostgreSQL cases; that result alone does not verify Day 10 concurrency.

Day 10 is ready for manual review. Advance to Day 11 after review and merge.
