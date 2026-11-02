# Day 43 — Simulate dependency failures

## Scope

Day 13 already verifies a clean PostgreSQL stop and restart. This exercise adds a
network partition: PostgreSQL stays running while the disposable API loses access
to it. The goal is to observe liveness, readiness and recovery behavior, and to
confirm that a payment written before the fault remains available afterward.

This is a local resilience exercise, not a production failover or load test. It
does not add application retry behavior; timeout and resilience patterns belong
to Day 44.

## Run

Requires PowerShell 7 and Docker Desktop with the Linux engine:

```powershell
pwsh -NoProfile -File .\scripts\test-dependency-failure.ps1
Get-Content .\target\day43-dependency-failure.json
```

The script creates a uniquely named Compose project with random local credentials
and a random loopback API port. It creates one synthetic payment, disconnects
only that project's PostgreSQL container from its Compose network, checks the
API and database remain running, expects readiness HTTP 503 and liveness `UP`,
then reconnects PostgreSQL and waits for readiness and container health to recover.
It confirms the original payment is still readable and removes only the disposable
containers, network and volume in `finally`.

## Verification — November 2, 2026

Result: **PASS**. During the network partition, PostgreSQL and API processes stayed
running, readiness returned 503, and liveness remained `UP`. After reconnecting,
readiness returned 200 and the pre-fault payment remained available. The temporary
Compose project and volume were removed. Machine-readable output:
[`target/day43-dependency-failure.json`](../target/day43-dependency-failure.json)
(generated locally; ignored by Git).

## Limits and next step

The fault was injected into one local PostgreSQL-backed Compose stack. This does
not demonstrate cloud database failover, replica behavior, packet loss under load,
or safety of payment calls during a real outage. The next roadmap item is Day 44:
timeouts, retries, circuit breaker and bulkhead. Keep production thresholds,
ingress configuration and any production fault injection as separately approved
operational work.
