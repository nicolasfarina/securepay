# SecurePay — OWASP ZAP passive DAST (CI)

## Goal and scope

DAST complements unit/integration tests, static analysis, Trivy, SpotBugs, SBOM
and the secret scan. This is an **authorized, passive-only** check of a
disposable containerized SecurePay instance and PostgreSQL database. It is
not an active vulnerability assessment, penetration test, or proof of freedom
from SQL injection. See the official documentation:
[ZAP passive scan](https://www.zaproxy.org/docs/docker/baseline-scan/) and
[Automation Framework](https://www.zaproxy.org/docs/desktop/addons/automation-framework/).

[CI workflow](../.github/workflows/dast.yml) starts an independent job on
pull requests and pushes to `main`, using `compose.dast.yaml`, not the
normal persistent `compose.yaml` deployment stack.

**Only** the internal Compose URL `http://api:8080` is scanned. The DAST
Compose stack publishes no host ports; database credentials, demo user
password and JWT signing key are random and generated for this CI execution.
The database is in a unique Compose volume, removed on exit via
`docker compose down --volumes --remove-orphans`. No production credentials
or production services are used.

## Coverage and attack safety

1. A read-only preflight verifies:
   - `GET /actuator/health/readiness` returns 200.
   - `GET /v3/api-docs` returns 200 in the disposable `postgres` profile.
   - Anonymous `GET /api/payments/<nonexistent-uuid>` returns 401.
   - Disposable user `customer-123` can log in and obtain a short-lived JWT.
   - Authenticated `GET /api/payments/<nonexistent-uuid>` returns 404 (not
     401/403), establishing successful customer authentication and routing.
2. ZAP receives the ephemeral JWT through `ZAP_AUTH_HEADER_VALUE`, restricted
   to the local host `api:8080` using `ZAP_AUTH_HEADER_SITE`. Its Requestor
   job performs GET-only checks against readiness, OpenAPI and the protected
   payment lookup. The traditional spider is limited to the disposable origin
   and Swagger UI, with form submission disabled, followed by passive scanning.
3. No `activeScan` job is configured, no state-changing payment or logout
   request is sent by the scan configuration, and the stack has no direct
   network access to production. The Swagger UI may load static assets; the
   scanner context excludes out-of-origin URLs.
4. Reports are `traditional-html` and `traditional-json`, **not** the
   `*-plus` templates that can include full HTTP messages/headers. The runner
   checks that neither the JWT nor demo password appears in the generated
   reports and removes unsafe reports instead of uploading them. No raw HTTP
   trace or ZAP process log is uploaded.

ZAP requestor HTTP response-code checks provide basic authenticated path
coverage; this is **not complete authenticated API coverage**. A future
active-only, explicitly authorized and isolated test may cover injection
behavior. API paths that require seeded payment data or POST mutations are
not explored by this passive job.

## Threshold and evidence

The ZAP Automation `exitStatus` job treats **Medium/High as blocking** and
**Low as a warning**. The runner accepts exit 0 (clean) and exit 2 (reviewable
low-severity warning), and fails for 1 or any other status. Errors such as
unreachable target, missing token, wrong HTTP smoke-test response or missing
report fail. Do not treat a warning exit as 'no findings'.

The JSON/HTML reports and `coverage.json` are uploaded for **14 days**
under `securepay-zap-passive-report`; no bearer tokens should be stored.
Review the exact rule ID, confidence, affected path and reproducibility
before declaring an alert a false positive. Any suppression needs an
explicit justification, owner and expiration date; there is no blanket
suppression of passive rules in this baseline.

Failures in existing unit/integration or security jobs remain independent
gates. CI output is evidence of a disposable laboratory run, not production
security or deployment approval.

## Reproduce locally (explicit opt-in)

From a trusted workstation with Docker, Docker Compose, OpenSSL and Python 3:

```bash
SECUREPAY_ALLOW_DAST_LOCAL=yes bash scripts/run-dast.sh
```

The script accepts no user-supplied remote target and cleans up the
disposable Compose project. Local execution may download ZAP and Docker
images. No deployment or merge is performed.

## Known limitations / follow-up

- The ZAP container image currently uses the official `stable` tag.
  Before long-term production gating, pin an immutable digest with an
  approved update policy.
- Swagger UI is explored with GET-only spidering. There is no active
  fuzzing, SQL injection exploitation or dangerous endpoint mutation.
- Periodic manual review is needed for low-severity alerts; scan coverage
  and passive rules can change with upstream ZAP updates.
- Verify execution time, false-positive triage and report sensitivity on
  pull request CI before making the job a required branch-protection check.
