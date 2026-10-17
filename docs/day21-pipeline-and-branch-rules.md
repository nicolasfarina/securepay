# Day 21 — DevSecOps pipeline and main branch rules

## What CI protects

Every pull request runs the `verify` job. It checks Maven dependencies, builds and scans the hardened container image, generates the image SBOM, runs the H2-backed unit/HTTP integration suite, runs the PostgreSQL integration suite, and uploads reports and both SBOMs. The separate `gitleaks` check scans the repository's full history for exposed secrets.

Pushes to `main`, `feat/**`, and `codex/**` also run the applicable checks. New pushes cancel older in-progress runs for the same branch or pull request so stale builds do not waste runner time.

## Active main branch rules

The repository has an active branch ruleset named `Protect main`, targeting the default branch (`main`). It applies these rules:

- Require a pull request before merging.
- Require these status checks: `verify` and `gitleaks`.
- Require the branch to be up to date before merging.
- Block force pushes and branch deletion.
- Do not allow bypassing the rules.
- Keep required approvals at zero while this is a single-maintainer project; add an approval requirement when another maintainer can review changes.

The required status checks are `verify` and `gitleaks`; branches must be up to date. The ruleset has no bypass actors, requires no approvals for this single-maintainer project, and blocks deletion and force pushes.

## Current repository status

The ruleset was verified active on October 17, 2026. CI also builds and scans the image and runs PostgreSQL integration tests against the same PostgreSQL 18.6 image used by Compose. The Compose persistence test uses its own disposable project and volume; it does not migrate or delete an existing PostgreSQL 17 volume.
