# Day 12 — Build and run the SecurePay image

Implemented and verified locally on October 16, 2026. This branch depends on Day 11 (`codex/day11-integration-tests`); review and merge Days 10, 11 and 12 in order. No automatic merge or deployment is enabled.

## What we are learning

An **image** is a packaged application and runtime. A **container** is a running instance of an image. Building an image does not start the API.

The two Dockerfile stages have different jobs:

```text
Source + pom.xml
      |
      v
build: Maven + Java 25 JDK -> compile -> tests -> executable JAR
      |
      | copy only the JAR
      v
runtime: Java 25 JRE + app.jar -> running API container
                                      |
                                      v
                               PostgreSQL container
```

The JDK includes tools for compiling Java. The JRE runs the compiled application. Keeping Maven, compiler and source in the build stage reduces what the final image needs to contain.

## Packaging decisions

- The existing two-stage Dockerfile now names both stages, runs `mvn -B -Duser.timezone=UTC verify` during the build instead of skipping tests, and copies only the resulting JAR into the runtime stage.
- Maven 3.9.16/Temurin 25 and Temurin 25 JRE Alpine base images are pinned to their multi-platform index digests, resolved from the official registry on the verification date. Review and deliberately update these pins for base-image fixes; pinning is not vulnerability scanning or a claim that the image is permanently safe.
- A BuildKit cache mount reuses Maven downloads. Copying `pom.xml` before source keeps dependency resolution separate from source changes. Docker can reuse a previously successful test layer when its inputs have not changed; source changes invalidate that layer.
- UID/GID `10001:10001` is explicit. The JAR is read-only (`0444`); the API needs no root privileges.
- The exec-form entrypoint runs Java as PID 1, allowing Docker's SIGTERM to reach Spring's graceful shutdown handling. Heap sizing retains `MaxRAMPercentage=75.0`; Java timezone is UTC.
- `.dockerignore` excludes local build outputs, version-control/editor data, environment files, logs and artifacts from the build context. Runtime credentials are supplied when starting containers and are not baked into the image.
- Production-profile verification supplies explicit database, demo password and JWT signing values. It disables OpenAPI/Swagger using the existing profile. The in-memory demo identities are still a learning-project authentication design, not an external production identity service.

No application Java behavior changed during Day 12. Compose and service health-check improvements remain Day 13; scanning/hardening work later in the roadmap is not represented as completed.

## Reproduce

Run from the repository root in PowerShell 7 with Docker Desktop running:

```powershell
# Build from source, run build-time tests and exercise the resulting container.
.\scripts\test-container.ps1

# Or build separately, then reuse that image for the runtime checks.
docker build --progress=plain -t securepay:day12-local .
.\scripts\test-container.ps1 -SkipBuild
```

Host Java/Maven are not required. A first build downloads the base images and Maven dependencies; subsequent builds can reuse caches. Nothing is pushed to a registry.

The verification script uses unique resource names and a random loopback API port. PostgreSQL has no published host port. It generates disposable database/demo/JWT values, starts the image with the `prod` profile, a 768 MiB memory limit, a read-only root filesystem, writable temporary `/tmp`, dropped Linux capabilities and `no-new-privileges`. Its containers, volume and network are removed in `finally`, including on failed verification. The built image and local evidence remain available. Existing local Swagger/API/database sessions are left running.

## Verified results

Docker Desktop Linux engine 29.8.1, `linux/amd64`. The source build used Java 25.0.4.1 and Maven 3.9.16; the runtime smoke test used PostgreSQL 17.11.

| Check | Result |
| --- | --- |
| Build-time Maven verification | 51 executed tests passed; five PostgreSQL-only tests explicitly skipped; JAR built |
| Runtime stage contents | Java available; Maven, `javac` and `/workspace` source/build tree absent |
| Production-profile readiness | HTTP 200, status UP with PostgreSQL |
| OpenAPI and Swagger in production profile | HTTP 404 |
| Process and filesystem | UID 10001, Java is PID 1, read-only root enabled |
| Unauthenticated payment creation | HTTP 401 |
| Customer login and valid payment creation | Login succeeds; payment returns HTTP 201 |
| Equivalent retry | HTTP 201; same payment fields and amount, including timestamps |
| Changed amount with same key | HTTP 409 |
| Negative amount with another key | HTTP 400 |
| Customer accessing admin payments | HTTP 403 |
| Stored rows after creation/retry/rejections | One payment and one idempotency record |
| Docker stop | Exit 143 after SIGTERM, no OOM kill; logs confirm graceful shutdown and connection-pool closure |
| Cleanup | Test API/database containers, volume and network removed; existing demo containers preserved |

PostgreSQL concurrency is not exercised inside `docker build`; the complete 56-case PostgreSQL verification remains available through `scripts/test-integration.ps1` from Day 11. The packaged production runtime is checked separately by the script above.

Local image `securepay:day12-local` was measured at **434,502,545 bytes** (about 414.4 MiB, Docker's local image size). This is not compressed registry transfer size, and no earlier-image size reduction is claimed.

Evidence is local and ignored under `target/day12-evidence/`: `build.log`, `local-run.log`, `container-results.json` and `api.log`. The image ID recorded by Docker is `sha256:ca4c07fd9456bcb4dfc799fe276f125dea45e396b2eb98d94df6625b8de8a643`; rebuilds may produce different image IDs because the application build itself is not claimed to be byte-for-byte reproducible.

Day 12 implementation and local verification are complete. Manual review and merge remain pending. Day 13 will focus on running API and PostgreSQL together with Compose and health checks.
