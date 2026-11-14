# Temporary security-gate operation

## Why the checks are separated

The default branch already contains a reported HIGH finding in the Prometheus monitoring dependency/image. The functional test job (`verify`) must still run to completion so development work can be reviewed while the baseline finding is remediated. The independent `security` job runs Trivy 0.76.2 across the repository dependencies and relevant images, records each scan in the GitHub check output, and fails if a scan finds a blocking issue or cannot complete.

No vulnerability is ignored by a new exception or allowlist. Individual Trivy step failures are collected so the remaining scans and SBOM can complete; the final enforcement step keeps the `security` job failed.

## Temporary merge policy

During remediation, `Protect main` requires `verify` and `gitleaks`; the separate `security` check remains visible but is temporarily not a required status check. This permits reviewed, functionally verified changes to merge while the known default-branch finding is being fixed. It does not authorize production deployment or real payment processing.

## Restore the security gate

After the configured Trivy scans report no blocking findings on the default branch and on a pull request against it:

1. In the repository ruleset **Protect main**, add the GitHub Actions status check `security` to the required status checks, keeping `verify` and `gitleaks` required.
2. Confirm a test pull request shows `security` required and green before merging.
3. Record the ruleset change and verification here.

The current Trivy policy scans HIGH and CRITICAL vulnerabilities with a fix available (`ignore-unfixed: true`). “Green” therefore means no findings under that configured gate, not zero vulnerabilities of every severity or exploitability category.
