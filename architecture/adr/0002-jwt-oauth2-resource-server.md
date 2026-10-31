# ADR 0002: JWT validation with Spring OAuth2 Resource Server

Spanish version: [Leer en español](0002-jwt-oauth2-resource-server.es.md)

## Status

Proposed; implemented on the PR branch, pending review and explicit merge approval.

## Context

The root application issued and parsed HS256 JWTs itself, using regular expressions for claims.
A valid signature must not grant access to an unknown, disabled or differently privileged account.
Payment ownership and rate limiting depend on the authenticated account name.

## Decision

Use the Spring Boot 4.1.2 managed starter `spring-boot-starter-security-oauth2-resource-server`,
NimbusJwtEncoder for issuance and NimbusJwtDecoder for verification. Remove the custom JWT
filter and parser. Keep the existing login API, HS256 secret configuration, issuer `securepay`,
role claim and expiration setting. This is a library migration, not an identity-provider migration.

Explicitly allow HS256 only. Require issuer, nonblank string subject and role, and expiration.
Validate expiration and not-before with zero clock tolerance to preserve the previous expiry
policy; hosts must have synchronized clocks. Tokens without expiration are rejected even though
the standard timestamp validator alone permits that claim to be absent.

After verification, load the current account and reject unknown, disabled, locked, expired or
credentials-expired accounts and role mismatches. Use the account's authorities and UserDetails
principal, preserving `Authentication.getName()` for ownership and per-user limits.
Run the existing rate-limit filter after BearerTokenAuthenticationFilter. Preserve 401/403
status handling and the outer correlation filter.

## Alternatives

RS256/JWKS separates issuers from verifiers and simplifies distributing public verification keys,
but requires a key lifecycle and identity-provider decision. Retain HS256 for this single-process
lab and evaluate RS256/JWKS separately. Do not introduce a second signing scheme during this migration.

## Consequences and remaining work

- Existing unexpired HS256 tokens with the required claims remain structurally compatible.
- Invalid Bearer credentials now fail on public endpoints too; the standard resolver also handles
  the Bearer scheme case-insensitively. No query or form-body tokens are enabled.
- Validation is maintained by Spring/Nimbus; business identity checks remain application policy.
- The default access-token lifetime remains 3600 seconds. Shortening it is still a separate decision.
- There is no individual-token logout/revocation, refresh-token flow, denylist or managed key rotation.
  Disabling an account invalidates its tokens on the next request; this does not replace individual revocation.
  No refresh tokens are added without a lifecycle and storage design.
- Rate-limit state remains in memory per instance; distributed enforcement and trusted-proxy deployment
  tests remain open. DAST with authenticated access and reports remains open.
- Root Maven tests cover HTTP authentication, tampering, algorithms, claims, ownership, 401/403/429,
  account state, correlation/MDC and observability. PostgreSQL tests also run in CI.
  Independent `services/` modules are outside this change and root verification.

## References

- [Spring Security JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Grupo Farina migration ticket](https://app.notion.com/p/400ec61028f081cdbd27f7dee52ead52)
- [Rate limiting and token lifecycle ticket](https://app.notion.com/p/400ec61028f0813584bec8a215f733da)
