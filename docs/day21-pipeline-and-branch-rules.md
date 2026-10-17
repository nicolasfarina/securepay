# Day 21 — DevSecOps pipeline and main branch rules

## What CI protects

Every pull request runs the `verify` job. It checks Maven dependencies, builds and scans the hardened container image, generates the image SBOM, runs the H2-backed unit/HTTP integration suite, runs the PostgreSQL integration suite, and uploads reports and both SBOMs. The separate `gitleaks` check scans the repository's full history for exposed secrets.

Pushes to `main`, `feat/**`, and `codex/**` also run the applicable checks. New pushes cancel older in-progress runs for the same branch or pull request so stale builds do not waste runner time.

## Main branch rule to enable

GitHub branch protection is repository configuration; committing a workflow or this document does not enable it. In the repository settings, create a branch ruleset targeting `main` with:

- Require a pull request before merging.
- Require these status checks: `verify` and `gitleaks`.
- Require the branch to be up to date before merging.
- Block force pushes and branch deletion.
- Do not allow bypassing the rules.
- Keep required approvals at zero while this is a single-maintainer project; add an approval requirement when another maintainer can review changes.

These checks are visible on the Day 19–20 PR as successful GitHub Actions checks. Confirm the exact names shown in GitHub when selecting required checks.

## Current repository status

As of October 17, 2026, `main` is not protected and the repository has no rulesets. The GitHub connection available to this task can read those settings but cannot change them. Enable the rule above in GitHub's repository settings to complete Day 21.
