package com.securepay.integration;

import com.jayway.jsonpath.JsonPath;
import com.securepay.support.TestJwtTokens;
import com.securepay.security.TokenRevocationService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import com.securepay.support.PostgresTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the running server, security filters, validation, persistence and error dispatch. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "securepay.rate-limit.login.max-attempts=1000",
        "securepay.rate-limit.payments.max-requests=1000",
        "spring.datasource.url=jdbc:h2:mem:securepay_day11;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "securepay.security.jwt-secret=" + ApiEndToEndTest.TEST_SECRET,
        "securepay.security.demo-password=" + ApiEndToEndTest.TEST_PASSWORD
})
class ApiEndToEndTest {
    static final String TEST_SECRET = "day11-integration-only-signing-key-change-me";
    static final String TEST_PASSWORD = "day11-integration-only-password";
    private static final String PAYMENT = "{\"merchantId\":\"merchant-456\",\"amount\":10.00,\"currency\":\"USD\"}";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate database;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private TokenRevocationService tokenRevocationService;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String customer;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if ("true".equals(System.getenv("SECUREPAY_POSTGRES_TEST"))) PostgresTestDatabase.configure(registry);
    }

    @BeforeEach
    void prepare() throws Exception {
        database.update("delete from revoked_tokens");
        database.update("delete from idempotency_records");
        database.update("delete from payments");
        customer = login("customer-123");
    }

    @Test
    void accessTokensExpireAfterFifteenMinutesAndLogoutRevokesOnlyThatToken() throws Exception {
        String firstToken = login("customer-123");
        String secondToken = login("customer-123");
        var jwt = jwtDecoder.decode(firstToken);
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));
        assertThat(jwt.getId()).isNotBlank();

        var logout = client.send(request("/auth/logout", firstToken)
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        expectStatus(logout, 204);
        expectStatus(get("/api/admin/payments", firstToken), 401);
        expectStatus(get("/api/admin/payments", secondToken), 403);

        String storedHash = database.queryForObject(
                "select token_id_hash from revoked_tokens", String.class);
        assertThat(storedHash).hasSize(64).isNotEqualTo(jwt.getId());
        assertThat(database.queryForObject("select count(*) from revoked_tokens", Long.class)).isEqualTo(1);
    }

    @Test
    void logoutRequiresAuthentication() throws Exception {
        var logout = client.send(request("/auth/logout", null)
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        expectStatus(logout, 401);
        assertThat(database.queryForObject("select count(*) from revoked_tokens", Long.class)).isZero();
    }

    @Test
    void expiredRevocationRowsAreRemoved() {
        database.update("insert into revoked_tokens(token_id_hash, expires_at, revoked_at) values (?, ?, ?)",
                "a".repeat(64), java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(120)));
        tokenRevocationService.removeExpiredRevocations();
        assertThat(database.queryForObject("select count(*) from revoked_tokens", Long.class)).isZero();
    }

    @Test
    void createReplayRetrieveAndAdminListingUseTheSamePersistedPayment() throws Exception {
        String key = UUID.randomUUID().toString();
        var created = post("/api/payments", customer, key, PAYMENT);
        expectStatus(created, 201);
        var body = JsonPath.parse(created.body());
        String id = body.read("$.id");
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(body.<String>read("$.customerId")).isEqualTo("customer-123");
        assertThat(body.<String>read("$.merchantId")).isEqualTo("merchant-456");
        assertThat(body.<Number>read("$.amount").doubleValue()).isEqualTo(10.0);
        assertThat(body.<String>read("$.currency")).isEqualTo("USD");
        assertThat(body.<String>read("$.status")).isEqualTo("PENDING");
        assertThat(body.<String>read("$.createdAt")).isNotBlank();
        assertThat(body.<String>read("$.updatedAt")).isNotBlank();

        var replay = post("/api/payments", customer, key, PAYMENT);
        expectStatus(replay, 201);
        assertThat(JsonPath.parse(replay.body()).jsonString()).isEqualTo(body.jsonString());
        var retrieved = get("/api/payments/" + id, customer);
        expectStatus(retrieved, 200);
        assertThat(JsonPath.parse(retrieved.body()).jsonString()).isEqualTo(body.jsonString());
        var adminList = get("/api/admin/payments", login("admin-123"));
        expectStatus(adminList, 200);
        assertThat(JsonPath.<java.util.List<String>>read(adminList.body(), "$[*].id")).containsExactly(id);
        assertThat(database.queryForObject("select count(*) from payments", Long.class)).isEqualTo(1);
        assertThat(database.queryForObject("select count(*) from idempotency_records", Long.class)).isEqualTo(1);
    }

    @Test
    void otherCustomersCannotReadPaymentButOwnerStillCan() throws Exception {
        var created = post("/api/payments", customer, "ownership-key", PAYMENT);
        expectStatus(created, 201);
        String id = JsonPath.read(created.body(), "$.id");
        assertProblem(get("/api/payments/" + id, login("customer-789")), 404);
        expectStatus(get("/api/payments/" + id, customer), 200);
        assertProblem(get("/api/payments/" + UUID.randomUUID(), customer), 404);
    }

    @ParameterizedTest
    @CsvSource({"customer-123,customer-789", "customer-789,customer-123"})
    void paymentReadAccessMatrixEnforcesRoleAndOwnership(String owner, String otherCustomer) throws Exception {
        String ownerToken = login(owner);
        String otherToken = login(otherCustomer);
        var created = post("/api/payments", ownerToken, "access-matrix", PAYMENT);
        expectStatus(created, 201);
        String id = JsonPath.read(created.body(), "$.id");
        String path = "/api/payments/" + id;

        var anonymous = get(path, null);
        expectStatus(anonymous, 401);
        assertThat(anonymous.body()).doesNotContain("\"customerId\"", "merchant-456", "\"amount\"");
        var admin = get(path, login("admin-123"));
        expectStatus(admin, 403);
        assertThat(admin.body()).doesNotContain("\"customerId\"", "merchant-456", "\"amount\"");

        var owned = get(path, ownerToken);
        expectStatus(owned, 200);
        assertThat(JsonPath.<String>read(owned.body(), "$.customerId")).isEqualTo(owner);
        assertThat(JsonPath.<String>read(owned.body(), "$.id")).isEqualTo(id);
        var hidden = get(path, otherToken);
        assertProblem(hidden, 404);
        assertThat(hidden.body()).doesNotContain(owner, "merchant-456", "\"amount\"");

        String missingId = UUID.randomUUID().toString();
        var missing = get("/api/payments/" + missingId, otherToken);
        assertProblem(missing, 404);
        assertThat(hidden.headers().firstValue("Content-Type"))
                .isEqualTo(missing.headers().firstValue("Content-Type"));
        // Normalize only the requested UUID: the rest of the public error must be the same.
        assertThat(JsonPath.<java.util.Map<String, Object>>read(hidden.body().replace(id, "<id>"), "$"))
                .isEqualTo(JsonPath.read(missing.body().replace(missingId, "<id>"), "$"));
    }

    @ParameterizedTest
    @CsvSource({"customer-123,customer-789", "customer-789,customer-123"})
    void suppliedCustomerIdCannotOverrideAuthenticatedOwner(String caller, String claimedOwner) throws Exception {
        String callerToken = login(caller);
        String payload = PAYMENT.replace("{", "{\"customerId\":\"" + claimedOwner + "\",");
        var created = post("/api/payments", callerToken, "forged-owner", payload);
        expectStatus(created, 201);
        String id = JsonPath.read(created.body(), "$.id");
        assertThat(JsonPath.<String>read(created.body(), "$.customerId")).isEqualTo(caller);
        assertThat(database.queryForObject("select customer_id from payments where id = ?", String.class,
                UUID.fromString(id))).isEqualTo(caller);
        expectStatus(get("/api/payments/" + id, callerToken), 200);
        assertProblem(get("/api/payments/" + id, login(claimedOwner)), 404);
        assertThat(database.queryForObject("select count(*) from payments", Long.class)).isEqualTo(1);
    }

    @Test
    void alteredJwtPayloadCannotImpersonateAdminWithOriginalSignature() throws Exception {
        expectStatus(get("/api/admin/payments", login("admin-123")), 200);
        expectStatus(get("/api/admin/payments", customer), 403);
        String[] parts = customer.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"sub\":\"customer-123\"", "\"role\":\"CUSTOMER\"");
        String alteredPayload = payload.replace("\"sub\":\"customer-123\"", "\"sub\":\"admin-123\"")
                .replace("\"role\":\"CUSTOMER\"", "\"role\":\"ADMIN\"");
        String alteredToken = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(alteredPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        expectStatus(get("/api/admin/payments", alteredToken), 401);
        expectStatus(post("/api/payments", alteredToken, "altered-token", PAYMENT), 401);
        assertNoPayments();
        // A rejected token must not break subsequent valid authentication.
        expectStatus(get("/api/admin/payments", login("admin-123")), 200);
    }

    @Test
    void conflictUsesProblemDetailsAndPreservesOriginalPayment() throws Exception {
        var created = post("/api/payments", customer, "conflict-key", PAYMENT);
        expectStatus(created, 201);
        var conflict = post("/api/payments", customer, "conflict-key", PAYMENT.replace("10.00", "20.00"));
        assertProblem(conflict, 409);
        String id = JsonPath.read(created.body(), "$.id");
        var original = get("/api/payments/" + id, customer);
        expectStatus(original, 200);
        assertThat(JsonPath.<Number>read(original.body(), "$.amount").doubleValue()).isEqualTo(10.0);
        assertThat(database.queryForObject("select count(*) from payments", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("invalidPayments")
    void invalidPaymentDataReturns400WithoutWritingRows(String payload) throws Exception {
        expectStatus(post("/api/payments", customer, "invalid-key", payload), 400);
        assertNoPayments();
    }

    static Stream<String> invalidPayments() {
        return Stream.of(
                PAYMENT.replace("merchant-456", ""),
                PAYMENT.replace("merchant-456", "m".repeat(129)),
                PAYMENT.replace("10.00", "null"),
                PAYMENT.replace("10.00", "0"),
                PAYMENT.replace("10.00", "-1"),
                PAYMENT.replace("10.00", "0.001"),
                PAYMENT.replace("10.00", "1.12345"),
                PAYMENT.replace("10.00", "1000000000000000"),
                PAYMENT.replace("USD", "usd"),
                PAYMENT.replace("USD", "US"),
                "{}", "{invalid json");
    }

    @ParameterizedTest
    @MethodSource("invalidKeys")
    void invalidIdempotencyKeyReturns400WithoutWritingRows(String key) throws Exception {
        expectStatus(post("/api/payments", customer, key, PAYMENT), 400);
        assertNoPayments();
    }

    static Stream<String> invalidKeys() {
        return Stream.of(null, "", " ", "k".repeat(256));
    }

    @Test
    void databaseLengthBoundariesAreAccepted() throws Exception {
        expectStatus(post("/api/payments", customer, "k".repeat(255),
                PAYMENT.replace("merchant-456", "m".repeat(128))), 201);
        assertThat(database.queryForObject("select count(*) from payments", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/payments/not-a-uuid", "/api/payments/00000000-0000-0000-0000-000000000000"})
    void invalidAndMissingPaymentIdsHaveClientErrorResponses(String path) throws Exception {
        expectStatus(get(path, customer), path.endsWith("not-a-uuid") ? 400 : 404);
    }

    @Test
    void permissionsRejectAnonymousAndAdminPaymentCreation() throws Exception {
        expectStatus(post("/api/payments", null, "anonymous", PAYMENT), 401);
        expectStatus(get("/api/admin/payments", null), 401);
        expectStatus(post("/api/payments", login("admin-123"), "admin", PAYMENT), 403);
        expectStatus(get("/api/admin/payments", customer), 403);
        assertNoPayments();
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void invalidTokensCannotCreatePaymentsOrReadAdminData(String token) throws Exception {
        expectStatus(post("/api/payments", token, "invalid-token", PAYMENT), 401);
        expectStatus(get("/api/admin/payments", token), 401);
        assertNoPayments();
    }

    static Stream<String> invalidTokens() {
        return Stream.of("invalid.jwt.token", "",
                TestJwtTokens.signed(TEST_SECRET, "customer-123", "CUSTOMER", -60),
                TestJwtTokens.signed("wrong-signing-key-for-day11-integration-tests", "admin-123", "ADMIN", 60),
                TestJwtTokens.signed(TEST_SECRET, "customer-123", "ADMIN", 60),
                TestJwtTokens.signed(TEST_SECRET, "unknown-user", "CUSTOMER", 60),
                TestJwtTokens.signed(TEST_SECRET, MacAlgorithm.HS256, b -> b.issuer("other")),
                TestJwtTokens.signed(TEST_SECRET, MacAlgorithm.HS256, b -> b.claims(c -> c.remove("iss"))),
                TestJwtTokens.signed(TEST_SECRET, MacAlgorithm.HS256, b -> b.claims(c -> c.remove("exp"))),
                TestJwtTokens.signed(TEST_SECRET, MacAlgorithm.HS256, b -> b.claims(c -> c.remove("sub"))),
                TestJwtTokens.signed(TEST_SECRET, MacAlgorithm.HS256, b -> b.claims(c -> c.remove("role"))),
                TestJwtTokens.signed(TEST_SECRET, MacAlgorithm.HS256, b -> b.claims(c -> c.remove("jti"))),
                TestJwtTokens.signed(TEST_SECRET, MacAlgorithm.HS256, b -> b.subject(" ")),
                TestJwtTokens.signed(TEST_SECRET, MacAlgorithm.HS256, b -> b.claim("role", " ")),
                TestJwtTokens.signed(TEST_SECRET, MacAlgorithm.HS256, b -> b.claim("role", java.util.List.of("CUSTOMER"))),
                TestJwtTokens.signed(TEST_SECRET, MacAlgorithm.HS256,
                        b -> b.notBefore(java.time.Instant.now().plusSeconds(600))),
                TestJwtTokens.signed(TEST_SECRET + TEST_SECRET, MacAlgorithm.HS512, b -> {}),
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                        "{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                        + Base64.getUrlEncoder().withoutPadding().encodeToString(
                        "{\"sub\":\"admin-123\",\"role\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8)) + ".");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"username\":\"customer-123\",\"password\":\"wrong\"}",
            "{\"username\":\"unknown-user\",\"password\":\"day11-integration-only-password\"}"
    })
    void incorrectCredentialsReturn401WithoutAToken(String payload) throws Exception {
        var response = post("/auth/login", null, null, payload);
        expectStatus(response, 401);
        assertThat(response.body()).doesNotContain("accessToken", TEST_PASSWORD, TEST_SECRET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"username\":\"\",\"password\":\"\"}", "{bad json"})
    void invalidLoginPayloadReturns400(String payload) throws Exception {
        expectStatus(post("/auth/login", null, null, payload), 400);
    }

    @Test
    void jwtContextDoesNotLeakToTheNextUnauthenticatedRequest() throws Exception {
        expectStatus(get("/api/admin/payments", login("admin-123")), 200);
        expectStatus(get("/api/admin/payments", null), 401);
        expectStatus(get("/error", null), 401);
    }

    @Test
    void healthAndApiDocumentationArePublic() throws Exception {
        for (String path : java.util.List.of("/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness")) {
            var response = get(path, null);
            expectStatus(response, 200);
            assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("UP");
            assertThat(response.body()).doesNotContain("components", TEST_PASSWORD, TEST_SECRET);
        }
        var docs = get("/v3/api-docs", null);
        expectStatus(docs, 200);
        assertThat(JsonPath.<String>read(docs.body(), "$.components.securitySchemes.bearerAuth.scheme"))
                .isEqualTo("bearer");
    }

    private String login(String username) throws Exception {
        var response = post("/auth/login", null, null,
                "{\"username\":\"" + username + "\",\"password\":\"" + TEST_PASSWORD + "\"}");
        expectStatus(response, 200);
        assertThat(JsonPath.<String>read(response.body(), "$.tokenType")).isEqualTo("Bearer");
        assertThat(JsonPath.<Number>read(response.body(), "$.expiresIn").longValue()).isEqualTo(900);
        return JsonPath.read(response.body(), "$.accessToken");
    }

    private HttpResponse<String> post(String path, String token, String key, String body) throws Exception {
        var request = request(path, token).header("Content-Type", "application/json");
        if (key != null) request.header("Idempotency-Key", key);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return client.send(request(path, token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, String token) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return request;
    }

    private void expectStatus(HttpResponse<String> response, int expected) {
        assertThat(response.statusCode()).as("%s: %s", response.uri(), response.body()).isEqualTo(expected);
    }

    private void assertProblem(HttpResponse<String> response, int status) {
        expectStatus(response, status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(status);
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).isNotBlank();
        assertThat(response.body()).doesNotContain("stackTrace", "org.hibernate", TEST_PASSWORD, TEST_SECRET);
    }

    private void assertNoPayments() {
        assertThat(database.queryForObject("select count(*) from payments", Long.class)).isZero();
        assertThat(database.queryForObject("select count(*) from idempotency_records", Long.class)).isZero();
    }
}
