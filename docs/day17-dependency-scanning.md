# Day 17 — Dependency vulnerability scanning

Implemented on October 17, 2026.

## Controls

- `.github/dependabot.yml` checks the root and both service Maven manifests, Dockerfile and Compose images, and GitHub Actions weekly. Compatible Spring and test dependency updates are grouped to keep review volume manageable.
- `.github/workflows/ci.yml` scans Maven manifests on every pull request and selected branch push with Trivy. Fixed high and critical vulnerabilities fail the CI gate; unfixable findings remain visible for follow-up without blocking unrelated changes.

## Verification and operating notes

GitHub runs the Trivy dependency scan on each pull request without requiring GitHub's dependency graph feature. Dependabot version updates run on GitHub's weekly schedule after this configuration is merged to the default branch.

Review Dependabot pull requests like code: inspect the advisory and affected paths, run the SecurePay verification workflow, and merge only after the change is understood. A scanner finding is a signal to investigate, not proof that the vulnerable code path is reachable.

The workflow blocks fixed high and critical vulnerabilities found in the scanned manifests; unresolved findings remain visible in the job output. Existing vulnerable dependencies are not automatically fixed by this PR. Enable repository Dependabot alerts and security updates in GitHub settings for ongoing vulnerability notifications and automatic security-update pull requests.
