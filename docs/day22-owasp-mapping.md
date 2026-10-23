# Day 22 — OWASP mapping and authorization evidence

Date: 2026-10-21 (America/Buenos_Aires).

Reviewed source: `275ac75514fcda460dd75fb12d100e04f47a584a` on `main`.

## Goal and scope

Map the ten OWASP web risk categories to SecurePay, find existing controls, and record evidence and remaining work. Completing this learning day means completing the initial map and authorization exercise. It does not mean every risk is fixed or the application is ready for production.

The roadmap's Days 23–32 follow the **OWASP Top 10:2021** categories. This document uses those IDs and adds their **2025** counterparts. The API Security Top 10 is a separate list; its full mapping belongs to Day 33.

## Risk → control → evidence → next work

All source and test links below refer to the reviewed code. A test's presence is distinct from a test run; the executed checks are recorded separately below.

| OWASP 2021 category | SecurePay example and existing control | Evidence | Remaining work / study day |
|---|---|---|---|
| A01 — Broken Access Control | One customer requests another customer's payment. The controller requires `CUSTOMER`; the service also checks ownership. Admin listing requires `ADMIN`. | [PaymentController](../src/main/java/com/securepay/payment/PaymentController.java), [PaymentService.getForCustomer](../src/main/java/com/securepay/payment/PaymentService.java), [AdminPaymentController](../src/main/java/com/securepay/payment/AdminPaymentController.java); ownership and role tests below. | Extend the access matrix to anonymous users, both customers and admin on the same payment endpoint. Review merchant authorization separately. Day 23. |
| A02 — Cryptographic Failures | Passwords use BCrypt. JWTs use HS256 signatures. Production secrets must come from environment configuration. | [SecurityConfig](../src/main/java/com/securepay/security/SecurityConfig.java), [JwtService](../src/main/java/com/securepay/security/JwtService.java), [production profile](../src/main/resources/application-prod.yml). | Verify TLS, encryption at rest, key storage and rotation in deployment. A signed JWT is not encrypted. Day 24. |
| A03 — Injection | Payment data enters the API. Bean Validation constrains fields; payment persistence uses Spring Data JPA repository methods rather than concatenated SQL. | [CreatePaymentRequest](../src/main/java/com/securepay/payment/CreatePaymentRequest.java), [PaymentRepository](../src/main/java/com/securepay/payment/PaymentRepository.java), [Day 25 inventory and test evidence](day25-injection.md); invalid input tests in [ApiEndToEndTest](../src/test/java/com/securepay/integration/ApiEndToEndTest.java). | Reassess if custom/native queries or dynamic sorting are added. Input-character policies remain a business-contract decision. Day 25 practice complete; CI check pending PR. |
| A04 — Insecure Design | Retries can create duplicate payments. Keys are scoped to the customer and bound to a request fingerprint; transactions and database constraints protect creation. State transitions are restricted. | [PaymentCreationTransaction](../src/main/java/com/securepay/payment/PaymentCreationTransaction.java), [migration](../src/main/resources/db/migration/V1__create_payment_tables.sql), [PaymentStatus](../src/main/java/com/securepay/payment/PaymentStatus.java), [threat model](../security/threat-models/securepay-payment-flow.md). | Review business abuse, provider integration and reconciliation. A local payment row does not prove at-most-once charging at a provider. Day 26. |
| A05 — Security Misconfiguration | Development defaults must not become production settings. The production profile requires secrets and disables API documentation; Compose restricts the runtime. | [production profile](../src/main/resources/application-prod.yml), [compose.yaml](../compose.yaml), [Dockerfile](../Dockerfile), [Day 14 notes](day14-configuration-verification.md). | Review deployment profiles, headers, CORS, secret access and exposed services. Production still uses demo in-memory accounts; an external identity provider remains future work. Day 27. |
| A06 — Vulnerable and Outdated Components | Libraries, images and tools can contain vulnerabilities. CI scans dependencies and images; Dependabot proposes updates; SBOMs inventory components. | [CI](../.github/workflows/ci.yml), [Dependabot](../.github/dependabot.yml), [Day 20 SBOM notes](day20-sbom.md). | Review new scan results and remediate findings. Current CI gates fixed HIGH/CRITICAL findings; that is not evidence of zero vulnerabilities. Day 28. |
| A07 — Identification and Authentication Failures | A forged, expired or invalid token must not grant access. The JWT filter validates signatures/claims and loads an enabled known user with a matching role. | [JwtAuthenticationFilter](../src/main/java/com/securepay/security/JwtAuthenticationFilter.java), [JwtService](../src/main/java/com/securepay/security/JwtService.java), invalid-token cases in [ApiEndToEndTest](../src/test/java/com/securepay/integration/ApiEndToEndTest.java). | Review the custom JWT parser, revocation, identity-provider migration and credential policy. Login rate limiting is planned for Day 34. Day 29. |
| A08 — Software and Data Integrity Failures | Untrusted builds or changed payment data can cross trust boundaries. Image digests, some pinned workflow actions, migration history and request fingerprints provide partial controls. | [Dockerfile](../Dockerfile), [CI](../.github/workflows/ci.yml), [secret scan](../.github/workflows/secret-scan.yml), [PaymentService](../src/main/java/com/securepay/payment/PaymentService.java). | Review remaining action tag pins, artifact signing/provenance and deployment verification. Provider webhook verification is not implemented in the current API. Day 30. |
| A09 — Security Logging and Monitoring Failures | Unauthorized requests must be detectable and investigated. Risks and required audit evidence are documented; health endpoints report service health. | [PAY-06 in risk register](../security/risk-register.md), [verification plan](../security/security-verification-plan.md), [application configuration](../src/main/resources/application.yml). | Implement and test security audit events, safe logging, retention and alerts. Health checks and ordinary framework logs are not a security audit trail. Day 31. |
| A10 — Server-Side Request Forgery (SSRF) | An attacker-controlled destination could make the server call an internal service. No user-controlled URL field or outbound HTTP client was found in the root application's `src/main` code reviewed today. | [CreatePaymentRequest](../src/main/java/com/securepay/payment/CreatePaymentRequest.java), root [application source](../src/main/java/com/securepay). | Reassess when provider calls or webhooks are added; design destination validation, egress limits and timeouts. No SSRF-specific protection or dynamic SSRF test is claimed. Day 32. |

## OWASP 2025 cross-reference

| Roadmap category (2021) | Corresponding 2025 category |
|---|---|
| A01 Broken Access Control | A01 Broken Access Control |
| A02 Cryptographic Failures | A04 Cryptographic Failures |
| A03 Injection | A05 Injection |
| A04 Insecure Design | A06 Insecure Design |
| A05 Security Misconfiguration | A02 Security Misconfiguration |
| A06 Vulnerable and Outdated Components | Expanded into A03 Software Supply Chain Failures |
| A07 Identification and Authentication Failures | A07 Authentication Failures |
| A08 Software and Data Integrity Failures | A08 Software or Data Integrity Failures |
| A09 Security Logging and Monitoring Failures | A09 Security Logging and Alerting Failures |
| A10 SSRF | Included in A01 Broken Access Control |

**A10:2025 — Mishandling of Exceptional Conditions** is an additional review topic. SecurePay has centralized payment errors and idempotency rollback/replay handling. Invalid-token failures clear the security context. Failure-injection tests for unexpected database/provider failures, safe error responses and fail-closed behavior still need a dedicated review. The roadmap keeps its existing study order.

## Authorization exercise

Authentication asks: **Who are you?** Authorization asks: **Can you access this payment?**

| Request | Expected result in SecurePay | Reason |
|---|---|---|
| Protected request without a valid token | 401 | Valid authentication is required. |
| Customer requests the admin listing | 403 | The customer does not have the required role. |
| `customer-789` requests a payment owned by `customer-123` | 404 | The API hides a payment that belongs to another customer. |
| `customer-123` requests their own existing payment | 200 | The customer owns the payment and has the required role. |
| Customer requests a nonexistent payment | 404 | The payment does not exist. |

`PaymentController.get` passes the authenticated user's name into `PaymentService.getForCustomer`. The service compares that name with the stored `customerId`. Both missing payments and ownership mismatches raise `PaymentNotFoundException`; `ApiExceptionHandler` maps it to 404. UUIDs and the `CUSTOMER` role do not replace this ownership check.

This is web **A01:2021 / A01:2025 Broken Access Control**. In the API list, the corresponding ownership risk is **API1:2023 Broken Object Level Authorization (BOLA)**. A 403 is a valid general denial; HTTP also permits 404 to hide the existence of a forbidden resource, which is SecurePay's chosen policy.

## Executed verification

Environment: Windows, Temurin Java 25.0.4.1, Maven 3.9.16. Default `local` profile; H2 database. The running-server test uses a temporary loopback port, actual login JWTs, security filters, service logic and persistence.

From the repository root:

```powershell
mvn "-Dtest=ApiEndToEndTest#otherCustomersCannotReadPaymentButOwnerStillCan" test
```

Result on 2026-10-21: **BUILD SUCCESS — 1 test, 0 failures, 0 errors, 0 skipped**.

The test logs in as `customer-123`, creates a payment (201), requests it as `customer-789` (404), retrieves it as the owner (200), and requests a missing UUID (404). It also checks the error response format and absence of selected internal details and test secrets.

The related suites were also executed:

```powershell
mvn -B -ntp "-Dtest=ApiEndToEndTest,AuthIntegrationTest,PaymentControllerIntegrationTest,PaymentServiceTest" test
```

Result on 2026-10-21: **BUILD SUCCESS — 51 tests, 0 failures, 0 errors, 0 skipped**.

| Suite | Tests | Scope |
|---|---|---|
| `ApiEndToEndTest` | 36 | Actual HTTP, JWT authentication, role/ownership denial, invalid input, persistence and safe errors. |
| `AuthIntegrationTest` | 6 | MockMvc login, invalid tokens, customer/admin permissions and absence of public registration. |
| `PaymentControllerIntegrationTest` | 6 | MockMvc payment creation, ownership, validation and idempotency errors. |
| `PaymentServiceTest` | 3 | Idempotency and controlled state transitions. |

The 51-test run includes the ownership test; it is not 52 distinct tests. Reports are generated in `target/surefire-reports/`. PostgreSQL concurrency, production TLS, operational alerts, dependency/image scans and deployment security are outside this local Day 22 test run.

## English practice (A2–B1)

- “I choose HTTP 401.” is grammatically correct. For another customer's payment, the technical answer in this application is 404.
- “Because this customer is not autorization.” → **“Because this customer is not authorized to access the payment.”** `Authorization` is a noun; `authorized` is an adjective.
- The learner correctly identified **200** for the owner. Full sentence: **“The owner receives HTTP 200 because they are authorized to access the payment.”**
- **“The token is valid, but the customer does not own the payment.”**

The evidence above records tests executed by the assistant. It does not claim the learner ran them independently or completed a separate final assessment.

## Next session — Day 23: Broken Access Control

Planned date: 2026-10-22. Start with the ownership test above and explain the four results in simple English. Then build an access matrix for anonymous requests, both customers and admin; compare ownership checks with role checks; identify any missing negative tests. Propose changes before editing application or test code.

Day 22's initial mapping and practical verification are complete. The remaining risks stay open for their planned study days.

## Sources

- [OWASP Top 10:2021](https://top10.owasp.org/2021/)
- [OWASP Top 10:2025 introduction and changes](https://top10.owasp.org/2025/0x00_2025-Introduction/)
- [OWASP API1:2023 — Broken Object Level Authorization](https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization/)
- [RFC 9110 — 401](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.5.2), [403 and optional 404 concealment](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.5.4)
