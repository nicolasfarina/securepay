# SecurePay — distributed rate limiting and trusted proxy policy

## Scope and threat model

This increment extends the already-merged Day 34 limiter without changing its
endpoint quotas: every POST /auth/login consumes a quota keyed by client IP,
and POST /api/payments consumes a quota keyed by authenticated account (with
an IP fallback if authentication is absent). Reads are not rate limited.

Previously, each application replica stored token buckets in a local
ConcurrentHashMap; clients could obtain independent quotas by hitting different
replicas. Behind a reverse proxy, getRemoteAddr() identified the proxy, not the
end user. Blindly reading X-Forwarded-For would let a direct client spoof
arbitrarily many addresses and bypass login throttling.

## Deployment policy

- The `local` profile retains the in-memory `RateLimitService` for
  deterministic developer tests and an isolated laboratory.
- `postgres` and `prod` use `PostgresRateLimitService`: one atomic row and
  transactional SELECT FOR UPDATE for each quota key across all replicas
  sharing the same PostgreSQL database. Never use the in-memory backend for
  multiple instances or production.
- The database's clock controls refill timing; quota keys are stored as
  SHA-256 digests, not raw usernames/IPs (hashing is pseudonymization, not
  anonymization).
- A shared store outage returns HTTP 503, never an allowed request or 429.
  429 remains reserved for an actually exhausted quota, with Retry-After.
- Idle bucket rows are deleted by the scheduled job after at least twice the
  configured refill period. Limit the public ingress rate and monitor table
  size: an adversary generating many distinct IPs can still cause high
  cardinality/DB load. A separate global admission control at the edge is
  needed for production-scale volumetric attacks.
- No refresh-token behavior or login/payment endpoint semantics change.

### Forwarded client IP: exact one-hop contract

`SECUREPAY_RATE_LIMIT_TRUSTED_PROXIES` is an optional comma-separated list
of numeric IPs/CIDRs **identifying direct reverse proxies**, for example
`10.20.0.0/24,2001:db8:ab::/64`.

The default is empty: the application **ignores all forwarded headers** and
uses the transport peer. Only when the immediate peer matches the configured
allowlist does the resolver accept X-Forwarded-For. The trusted edge proxy
**MUST overwrite**, not append, inbound X-Forwarded-For with exactly one
numeric IPv4/IPv6 client IP. Missing, duplicated, multi-hop, hostname or
malformed values from a trusted peer cause HTTP 400, rather than collapsing
or silently trusting an ambiguous address.

Do not configure a broad internal network as trusted unless every host in
that network is an authorized header-sanitizing reverse proxy. Network
security policy must prevent direct access to the API from untrusted hosts,
including hosts within any trusted CIDR. Configure trusted peer ranges only
after validating real load-balancer and Nginx behavior. Multi-hop XFF chains,
PROXY protocol, Forwarded (RFC 7239), and automatic container/proxy header
translation are intentionally unsupported by this strict contract. Do not
enable global Spring forward-header processing for client identity.

## SQL and rollout constraints

- Flyway V3 creates `rate_limit_buckets`; only migration-managed disposable
  PostgreSQL test databases have been authorized for this increment.
- Switching `postgres` or `prod` to the shared backend requires applying
  V3 via an approved change process. No persistent migrations or deployments
  are part of the PR.
- PostgreSQL is now on the request path for rate-limited POSTs. Verify
  connection pool capacity, p95/p99 latency, database availability,
  lock contention, metrics/alerts and rollback strategy before deployment.
- Optional `SECUREPAY_RATE_LIMIT_TRUSTED_PROXIES` must not be populated with
  arbitrary caller-controlled XFF addresses. Network/identity isolation
  remains an operational prerequisite.

## Required verification

1. Local unit tests: gradual refill; bounded in-memory keys; trusted and
   untrusted peers; malformed/duplicated XFF; HTTP 400 on invalid trusted-edge
   headers; HTTP 503 when the shared store is unavailable.
2. PostgreSQL test database: 12 concurrent consumers on the same key must
   produce exactly 3 permits for a capacity of 3, irrespective of which
   process handles the request; distinct authenticated customer keys are
   independent; key values are hashed; rejected quotas expose Retry-After.
3. Existing login 401, authorization 403, rate-limit 429, payment ownership
   and token revocation tests must remain green.
4. Verify the CI security checks and secret scan on the exact PR HEAD before
   requesting a merge decision.

## Known limitations

- Token bucket limits are request quotas, not defenses against expensive
  long-running handlers or unbounded distributed denial of service.
- PostgreSQL contention and latency may make this backend unsuitable for
  high-RPS production systems; a dedicated atomic key-value limiter or
  perimeter gateway policy may be preferable with measured load.
- A single trusted proxy can still misidentify clients if it fails to remove
  attacker-supplied forwarded headers; this must be tested at the real ingress.
- No production rollout, merge, secret rotation or persistent DB migration
  is authorized by this document.
