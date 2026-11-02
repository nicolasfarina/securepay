# Passive DAST on a disposable local API

The scope is a standalone SecurePay instance built from this checkout, running the
`local` profile with an in-memory H2 database and synthetic users/payments. ZAP
observes explicit requests; it never runs an active scanner, spider, fuzzer, or
OpenAPI importer. Downloading `/v3/api-docs` does not execute the described operations.

## Safety boundary

- Use only `compose.dast.yaml`, never combine it with production/persistent stacks.
- Both containers join a dedicated `internal: true` Docker network. No published
  ports, host networking, Docker socket, persistent database, production credentials,
  or external target option are provided. Image/dependency downloads occur before
  runtime isolation; ZAP update checks cannot leave the internal network.
- The target is fixed to `http://api:8080`; only simple local paths are accepted.
  Redirects and inherited HTTP proxies are disabled. ZAP's control API binds only
  to loopback inside its container, uses a generated key, runs in safe mode, and
  passively scans only the local context.
- Writes are limited to logins/logout and one fictional pending payment per run.
  The API's H2 database disappears when its container exits. Flyway runs only there.
- Do not extend this to production or an external host. A different environment
  requires a separately reviewed scope and explicit authorization.

The API usage follows the official [ZAP API reference](https://www.zaproxy.org/docs/api/)
and [scope documentation](https://www.zaproxy.org/docs/desktop/start/features/scope/).
The ZAP image is pinned by digest; review and rerun the suite when updating it.

## Run and inspect

From the repository root on Windows:

```powershell
./scripts/test-dast.ps1
Get-Content dast/reports/summary.json
```

Linux/macOS (use a unique project name if another copy is running):

```sh
mkdir -p dast/reports && chmod 777 dast/reports
docker compose -p securepay-dast -f compose.dast.yaml run --rm --no-deps zap python3 -m unittest discover -s /zap/wrk -p 'test_*.py'
docker compose -p securepay-dast -f compose.dast.yaml build api
docker compose -p securepay-dast -f compose.dast.yaml up --abort-on-container-exit --exit-code-from zap
# Always execute cleanup, including when any preceding command fails:
docker compose -p securepay-dast -f compose.dast.yaml down --volumes --remove-orphans
```

The PR workflow performs this automatically, with a timeout and unconditional
cleanup. It uploads only `summary.json` with seven-day retention. Local ZAP logs
are for debugging and are not uploaded; do not share raw proxy history, which can
contain credentials or tokens. The summary omits bodies, request headers, evidence,
and other-info fields. It contains only synthetic endpoint paths and alert metadata.

## Coverage and gates

The 17 HTTP checks cover readiness, local OpenAPI exposure, anonymous access,
invalid credentials/token, both customer logins and admin login, customer denial
of admin endpoints/metrics, admin access, creating/retrieving an owned payment,
hiding it from another customer, logout, and token revocation.

Any unexpected HTTP status, startup/API failure, passive-queue timeout, or Medium/
High passive alert fails the run. Low and Informational alerts remain visible for
review. No rule suppressions are configured. A completed passive run is not a
proof that the service has no vulnerabilities.

This scope does not validate TLS/HSTS at a real ingress, production Swagger
settings, distributed PostgreSQL quotas, persistent migrations, load behavior,
or every REST operation. Existing PostgreSQL integration tests are complementary.
Additional endpoints and hardening must be reviewed explicitly.

## First local verification, 2026-11-02

Application source: `56a3d950652aace5fb2ae8c9cddc44e3c82ffda5` (PR #47 merge).
ZAP 2.17.0, pinned image digest in Compose. All 17 HTTP checks passed, the passive
queue drained, and ZAP returned four Informational instances (rules 10111 and
10112: authentication/session detection); no Low, Medium, or High alerts.
Three safety/report tests passed. The stack had an internal network and no
published ports. The sanitized result is recorded in
[`dast/evidence/2026-11-02-local.json`](../dast/evidence/2026-11-02-local.json).
