# SecurePay security status — 2026-10-31

Reviewed base: `main`, `e06c204483e8719b7f083041975cc2a1bbba4a49`.
No open pull requests were returned at the start of this review.

| Area | Verified state | Remaining work |
| --- | --- | --- |
| Day 36: correlation, MDC, exceptions | Merged in [PR #40](https://github.com/nicolasfarina/securepay/pull/40); implementation and existing filter/HTTP/async tests inspected | No duplicate implementation needed |
| Rate limiting | Merged in [PR #34](https://github.com/nicolasfarina/securepay/pull/34); login per remote IP, payments per authenticated account, 429/Retry-After tests | Distributed state and production proxy policy |
| JWT Resource Server | Custom regex parser still present on reviewed main; replaced on this PR branch | Review and explicit merge approval |
| Token lifecycle | Default TTL 3600 seconds, account checked per request | Shorter TTL decision, individual revocation/refresh design and tests |
| DAST OWASP ZAP | No ZAP job in current workflows | Disposable authorized target, authenticated coverage, artifact report and failure threshold |
| Swagger | Production restriction merged in [PR #41](https://github.com/nicolasfarina/securepay/pull/41) | No duplicate implementation needed |

## Existing main checks

The CI after [PR #42](https://github.com/nicolasfarina/securepay/pull/42) completed successfully:
[CI 40399710371](https://github.com/nicolasfarina/securepay/actions/runs/40399710371),
[Secret Scan 40399710251](https://github.com/nicolasfarina/securepay/actions/runs/40399710251),
and [container publication 40399710262](https://github.com/nicolasfarina/securepay/actions/runs/40399710262).
The CI job's Maven, PostgreSQL, dependency/image scans, monitoring checks and SBOM steps were successful.
These are historical checks on the base commit, not validation of this PR.

## Priority and implementation

Replace custom JWT parsing first because it sits on every authenticated request.
The PR uses Boot-managed Spring Security 7.1.2 and Nimbus 10.9.1, preserves account-backed
authorization and adds negative HTTP cases plus account-state and algorithm tests.
See [ADR 0002](../architecture/adr/0002-jwt-oauth2-resource-server.md) for compatibility and limitations.

Root Maven verification is local with H2; PostgreSQL-only tests are explicitly opt-in.
Local `mvn -B -ntp -Duser.timezone=UTC verify` completed successfully on 2026-10-31:
143 cases, 138 passed, 5 PostgreSQL-only skipped, no failures or errors.
SpotBugs reported 0 findings and 0 errors.
Docker was not running locally during this review, so PostgreSQL and image vulnerability
checks for this branch must be evidenced by PR CI. Root verification does not cover the
standalone modules under `services/`. No merge, deployment, production scan or notification
to external recipients is part of this work.

## Grupo Farina tracking

- [JWT migration](https://app.notion.com/p/400ec61028f081cdbd27f7dee52ead52): this PR, not yet merged.
- [Rate limiting and token lifecycle](https://app.notion.com/p/400ec61028f0813584bec8a215f733da):
  rate limiting already merged; lifecycle remains partly open.
- [OWASP ZAP](https://app.notion.com/p/400ec61028f0811dbc83d2282b8f6cec): remains pending.
- [SecurePay project](https://app.notion.com/p/400ec61028f0810faca0ccddef7f47ab):
  Day 36 and main checks should not be treated as unstarted work.

## Token lifecycle implementation

The follow-up branch `codex/token-revocation-jti` implements the lifecycle design from the
[Grupo Farina ticket](https://app.notion.com/p/400ec61028f0813584bec8a215f733da): 15-minute
access tokens, random `jti`, PostgreSQL-backed per-token revocation through `POST /auth/logout`,
fail-closed validation, hourly cleanup, and no refresh-token flow. Tokens minted before rollout
without `jti` require a fresh login. The change is not merged or deployed; see its PR for checks.

OWASP ZAP with authenticated coverage, reports and a failure threshold remains the next separate
security-control task.
