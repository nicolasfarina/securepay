# Day 17 — Dependency vulnerability scanning

Implemented on October 17, 2026.

## Controls

- `.github/dependabot.yml` checks the root and both service Maven manifests, Dockerfile and Compose images, and GitHub Actions weekly. Compatible Spring and test dependency updates are grouped to keep review volume manageable.
- `.github/workflows/dependency-review.yml` checks pull-request dependency changes and blocks newly introduced high or critical vulnerabilities. It adds a short report to the PR when it fails.
- The review workflow uses read-only repository and pull-request permissions.

## Verification and operating notes

GitHub runs the dependency-review workflow on each pull request. The action compares the pull request dependency graph with its base branch, so verification requires GitHub's dependency graph to be available for this repository. Dependabot version updates run on GitHub's weekly schedule after this configuration is merged to the default branch.

Review Dependabot pull requests like code: inspect the advisory and affected paths, run the SecurePay verification workflow, and merge only after the change is understood. A scanner finding is a signal to investigate, not proof that the vulnerable code path is reachable.

The workflow blocks newly introduced high and critical vulnerabilities; existing vulnerable dependencies are not automatically fixed by this PR. Enable repository Dependabot alerts and security updates in GitHub settings for ongoing alerts and automatic security-update pull requests.
