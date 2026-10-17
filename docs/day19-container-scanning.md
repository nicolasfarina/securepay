# Day 19 — Container image scanning and hardening

Implemented on October 17, 2026.

## Controls

- CI builds `securepay:ci` from the production Dockerfile and scans the final runtime image with Trivy.
- The image scan fails on fixed high or critical operating-system and application-library vulnerabilities. Findings without a published fix remain visible but do not block while no safe upgrade exists.
- The runtime image is already a separate Java 25 JRE stage with the application JAR only, runs as UID/GID 10001, and uses a read-only JAR. Compose enforces a read-only root filesystem, writable temporary storage only, dropped capabilities, and `no-new-privileges`.
- Builder and runtime base images are pinned by digest. Digest pinning ensures repeatable inputs, while the scan checks those exact inputs for known vulnerabilities.

## Verification

Every pull request and selected branch push builds and scans the image before application tests. Review the Trivy output when a build fails, update the vulnerable base or library to a compatible fixed version, and rerun the complete test suite.

`mvn verify` validates the application, while `scripts/test-container.ps1` exercises the production-profile runtime, non-root identity, read-only root, API behavior, and graceful shutdown locally.
