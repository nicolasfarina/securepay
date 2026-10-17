# Day 18 — Secret scanning and credential hygiene

Implemented on October 17, 2026.

## Controls

- Gitleaks scans pull requests, pushes to `main` and `codex/*`, manual runs, and weekly scheduled runs.
- Checkout fetches full history so a credential removed from the latest tree can still be found in earlier commits.
- `.env`, `.env.*`, and `.envrc` are ignored by Git. The Docker build context already excludes environment files.
- The README tells contributors to revoke or rotate a real secret before removing it from source.

The workflow does not post PR comments. It uses the read-only GitHub token permissions needed for repository scanning and PR metadata; findings appear in the Actions run summary and uploaded report.

## Verification and response

The `Secret Scan` workflow runs in GitHub Actions. Review results before merge. A confirmed credential exposure requires immediate revocation/rotation, scope review, investigation of access logs, and removal from the repository history where practical. Removing a credential from the latest commit alone does not neutralize it.

This control detects patterns; it does not replace secret-manager access policy, credential rotation exercises, or review of application logs and deployment configuration.
