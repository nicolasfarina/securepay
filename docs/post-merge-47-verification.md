# PR #47 post-merge verification — 2026-11-02

GitHub REST PR metadata, `commits/main`, Actions runs and job steps were inspected
on 2026-11-02. Times below are UTC (Buenos Aires is UTC-3).

- [PR #47](https://github.com/nicolasfarina/securepay/pull/47) is closed and merged,
  `merged_at=2026-11-02T13:45:09Z` (10:45:09 Buenos Aires).
- PR head: `6a609bccf56bbdf3e512c139a3d20dc9b5c225b0`.
- Merge commit and observed main HEAD:
  [`56a3d950652aace5fb2ae8c9cddc44e3c82ffda5`](https://github.com/nicolasfarina/securepay/commit/56a3d950652aace5fb2ae8c9cddc44e3c82ffda5).
- All three post-merge runs below were `push` events on that exact SHA, created
  at `2026-11-02T13:45:12Z`.

| Workflow | Result | Evidence |
| --- | --- | --- |
| CI | success | [run 41455683686](https://github.com/nicolasfarina/securepay/actions/runs/41455683686), [verify job](https://github.com/nicolasfarina/securepay/actions/runs/41455683686/job/125311628795) |
| Secret Scan | success | [run 41455684305](https://github.com/nicolasfarina/securepay/actions/runs/41455684305), [gitleaks job](https://github.com/nicolasfarina/securepay/actions/runs/41455684305/job/125311631160) |
| Publish container image | failure | [run 41455683767](https://github.com/nicolasfarina/securepay/actions/runs/41455683767), [publish job](https://github.com/nicolasfarina/securepay/actions/runs/41455683767/job/125311629762) |

CI job steps confirm success for Java 25 verification, dependency/container/
monitoring vulnerability scans, unit/HTTP tests, full PostgreSQL integration
suite, and verification report/SBOM upload. Secret Scan checked full repository
history successfully. This is evidence of these configured checks, not a blanket
security certification.

## Concrete publication failure

The publish job failed in **Run tests**, `mvn -B test`, at 13:46:18Z:

```text
PaymentTracingTest.recordsFailureTypeWithoutExceptionMessage:62
expected: "payment.create"
 but was: "task tokenRevocationService.removeExpiredRevocations"
Tests run: 165, Failures: 1, Errors: 0, Skipped: 8
```

Docker setup, GHCR login, tag creation, and image publication were all skipped.
This job did not publish the merge image. No deployment state was inspected, so
no conclusion about production is implied.

The test selected the first span from a shared exporter; scheduled background
tasks can finish first. The proposed test change selects payment spans, retains
the status/privacy assertions and cardinality check, and deliberately inserts an
unrelated background span before each assertion path. Local Java 25 targeted
verification: **2 tests, 0 failures/errors/skips**. Application behavior and the
publication workflow are unchanged. The failed publish workflow was not retried,
because retrying could publish an image.

## DAST scope and remaining checks

See [passive-dast.md](passive-dast.md) for isolation, commands, coverage and local
results. The first local API image was built from the exact merge source; the
proposed application-source changes affect tests only.

Before considering a merge, inspect the new PR's CI, Secret Scan and Passive DAST
results on its latest revision. A separate owner decision is needed for any merge,
image publication/deployment, persistent migration, external scan or production
change. Real ingress/header behavior and distributed-quota operational checks
from PR #47 remain outside this local DAST scope.
