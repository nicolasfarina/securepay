# Day 15 — Continuous integration

Implemented on October 17, 2026.

## Workflow

`.github/workflows/ci.yml` runs on pull requests and pushes to the project branches. GitHub Actions provisions PostgreSQL 17.11, configures Java 25 with Maven dependency caching, and runs the default H2 verification suite followed by the HTTP and PostgreSQL concurrency tests against the service database. The two steps run sequentially because the test contexts share application state. Surefire reports are uploaded even when verification fails.

The PostgreSQL service uses disposable CI-only credentials and is reachable only within the job. No repository secrets are needed for verification. The publishing workflow remains separate and continues to own image publication.

## Verification

Review the workflow YAML and run both commands locally where applicable:

```powershell
mvn -B '-Duser.timezone=UTC' verify
$env:SECUREPAY_POSTGRES_TEST = 'true'
$env:SECUREPAY_TEST_POSTGRES_URL = 'jdbc:postgresql://localhost:5432/securepay_test'
$env:SECUREPAY_TEST_POSTGRES_USERNAME = 'securepay_test'
$env:SECUREPAY_TEST_POSTGRES_PASSWORD = 'securepay_test_password'
mvn -B '-Duser.timezone=UTC' verify
```

The full local PostgreSQL run uses a disposable database. Do not aim it at data that must be preserved. Repository branch protection must be configured separately to require the `verify` status check before merging; workflow YAML cannot enable that GitHub repository setting.
