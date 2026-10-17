# Day 13 — Compose, persistence and health checks

The initial Day 13 implementation was verified locally on October 16, 2026, from `fdf2761` on `codex/day11-integration-tests`. It was later incorporated into `main` through PR #6. This document preserves the original verification evidence; current PostgreSQL 18 behavior is noted below.

**Current image note:** the original Day 13 verification below used PostgreSQL 17.11. SecurePay later upgraded Compose and integration verification to PostgreSQL 18.6; the current volume layout and the required migration caution are documented in [PostgreSQL 18 volume and migration notes](postgres-18-volume-migration.md).

## Lesson

Docker Compose describes the API, PostgreSQL, network and persistent storage together. One command starts them:

```powershell
.\scripts\run-local.ps1 -ApiPort 8082 -ProjectName securepay-day13-demo
```

```text
Browser -> 127.0.0.1:8082 -> API container
                                |
                          postgres:5432
                                |
                         PostgreSQL container
                                |
                         named Docker volume
```

`postgres` is the Compose service's DNS name. Inside the API container, `localhost` means the API container itself, not the database or the user's computer. PostgreSQL does not publish a host port in the default stack. `compose.host-db.yaml` is an optional override for host database clients or a host Java process.

The named volume stores PostgreSQL files independently of either container. `docker compose down` preserves it; recreating the same project can read the payments again. `down --volumes` deletes the data. A volume is not a backup.

## Changes and decisions

- The original Day 13 evidence used the official `17.11-alpine` image pinned by multi-platform digest. Current Compose uses PostgreSQL 18.6 and mounts its named volume at `/var/lib/postgresql`; see the upgrade note linked above.
- The API starts after PostgreSQL becomes healthy using `depends_on: condition: service_healthy`. It has its own readiness health check. `run-local.ps1` uses Compose `up --build --wait --wait-timeout 120`, so success means both services are healthy rather than merely running.
- In the `postgres` application profile, readiness includes `readinessState` and `db`; Hikari connection/validation waits are bounded at 3000/1000 ms. A database outage therefore produces readiness HTTP 503 in bounded time. Liveness remains independent of database availability.
- `service_healthy` controls startup ordering. It does not propagate dependency health automatically after startup. The API's own health check detects an outage. An unhealthy status does not, by itself, restart the container; `restart: unless-stopped` concerns process/container exit. Database recovery was verified without restarting the API.
- The API retains Day 12's non-root image and runs with read-only root, temporary writable `/tmp`, dropped capabilities and no-new-privileges. Docker gets 30 seconds for graceful shutdown.
- Fixed `container_name` values were removed so different project names can coexist. Compose scopes container/network/volume names by project. API binding stays on loopback; its port is configurable. The test script requests a random port.
- The Compose demo uses the existing `postgres` profile, so Swagger remains available for learning. Demo passwords and the development JWT default remain explicitly local-only. Production profile behavior is separately verified by the Day 12 script; profile/configuration work remains Day 14.

## Reproduce

Requires PowerShell 7, Docker Desktop and a Compose version supporting `up --wait` and `--wait-timeout`. Verification used Compose v5.6.0 and Docker engine 29.8.1 on Linux/amd64.

```powershell
# Builds/tests the image and exercises the complete Compose lifecycle.
.\scripts\test-compose.ps1

# Reuse an image already built from this checkout.
docker build -t securepay:day13-local .
.\scripts\test-compose.ps1 -SkipBuild

# Keep a separate learning demo running.
.\scripts\run-local.ps1 -ApiPort 8082 -ProjectName securepay-day13-demo
docker compose --project-name securepay-day13-demo ps
docker compose --project-name securepay-day13-demo logs --tail 30 api

# Stop the learning demo but preserve payment data.
docker compose --project-name securepay-day13-demo down
```

The verification script generates a unique `securepay-check-*` project, random loopback port and disposable credentials. It creates a test payment, recreates the API, performs `down` without volume deletion and recreates the whole stack, then stops PostgreSQL to verify readiness, liveness and container health. After recovery it checks both the original payment and persisted idempotency record. Environment variables are restored and `finally` removes only that unique verification project and its disposable volume. Existing demos are preserved.

Do not use `down --volumes` on the learning demo if its data needs to be retained. PostgreSQL initialization variables apply when the volume is first initialized; changing a password environment value does not migrate credentials already stored in a volume.

## Verification results

| Check | Result |
| --- | --- |
| Default and optional host-port Compose configuration | Both pass `docker compose config --quiet` |
| Source image build | Maven verification passes: 51 executed tests; five PostgreSQL-only cases explicitly skipped |
| Initial `up --wait` | PostgreSQL and API both healthy |
| Create and equivalent retry | One payment ID |
| Recreate only API | Original payment remains readable |
| `down` without volumes, then full stack recreation | Same payment and idempotency record preserved in the named volume |
| Database outage | API readiness 503; liveness UP; container eventually unhealthy |
| Restart PostgreSQL | Both services recover to healthy; original payment readable without restarting API |
| Final database counts | One payment and one idempotency record |
| Verification cleanup | Disposable project containers/network/volume removed; prior local demos preserved |

Evidence is local and ignored under `target/day13-evidence/`: `build.log`, `local-run.log`, `compose-results.json`, `services.log` and `demo-start.log`. The earlier Day 10/11/12 documents are historical verification records; the roadmap records the current merge state.

This verifies a local Compose deployment, not production failover, backups, provider-side payments, load capacity or automatic recovery from every database failure. Complete PostgreSQL concurrency checks remain available in `scripts/test-integration.ps1`. Day 14 will address externalized configuration and environment profiles.
