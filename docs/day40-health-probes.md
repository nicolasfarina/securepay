# Day 40 — Liveness and Readiness

## Goal

Keep a temporary database outage from making the API restart-loop, while ensuring traffic and Compose startup only treat the API as ready when its required PostgreSQL dependency responds.

## Existing probe design

- Spring Boot health probes are enabled in `application.yml`.
- In the `postgres` profile, readiness includes `readinessState` and `db`. A database outage makes `/actuator/health/readiness` return HTTP 503.
- Liveness uses Spring Boot's liveness state and does not include the database. `/actuator/health/liveness` stays UP during a database outage so an orchestrator does not restart a healthy process just because a dependency is unavailable.
- Compose waits for PostgreSQL's `pg_isready` health check before starting the API. The API container health check calls its readiness endpoint, so Compose marks the API unhealthy while PostgreSQL is unavailable. An unhealthy health check reports status; it does not itself restart the container.
- Health details are hidden, and only the existing Actuator `health` and `metrics` endpoints are exposed.

## Verification

Ran `pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-compose.ps1` on October 26, 2026. The script built the image and ran an isolated Compose project with a unique name, random credentials, and its own disposable volume.

| Scenario | Result |
| --- | --- |
| PostgreSQL and API startup | Both healthy |
| Recreate API only | Existing payment remained available |
| Tear down/recreate full stack while preserving volume | Payment and idempotency record remained available |
| Stop PostgreSQL | Readiness returned 503; liveness remained UP; API container became unhealthy |
| Restart PostgreSQL | Both services recovered; payment remained available without restarting API |
| Final database state | One payment row and one idempotency row |
| Cleanup | Temporary project, network, and volume removed; pre-existing containers were left running |

Machine-readable result: `target/day13-evidence/compose-results.json` (`result: PASS`). The script also stores service logs in `target/day13-evidence/services.log`.

## Limits

This verifies local Compose behavior, not Kubernetes scheduling, cloud load-balancer behavior, or production database failover. Readiness is intentionally stricter than liveness because this payment API requires PostgreSQL to serve its core routes. If the service later gains routes that safely work without the database, reconsider whether one readiness group remains appropriate. Probe traffic and database outage behavior should also be observed under deployment load.
