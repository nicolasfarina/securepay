package com.securepay.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.securepay.support.PostgresTestDatabase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:securepay_day25_isolated;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "securepay.security.jwt-secret=day25-isolated-test-only-signing-key",
        "securepay.security.demo-password=day25-isolated-test-password"
})
class Day25MerchantLiteralTest {
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate database;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource
    static void configurePostgresWhenRequested(DynamicPropertyRegistry registry) {
        if ("true".equals(System.getenv("SECUREPAY_POSTGRES_TEST"))) {
            PostgresTestDatabase.configure(registry);
        }
    }

    @BeforeEach
    void clearIsolatedDatabase() {
        database.update("delete from idempotency_records");
        database.update("delete from payments");
    }

    @Test
    void apostropheIsStoredLiterallyAndOtherPaymentRemainsUnchanged() throws Exception {
        var login = send("POST", "/auth/login", null, null,
                "{\"username\":\"customer-123\",\"password\":\"day25-isolated-test-password\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        String token = JsonPath.read(login.body(), "$.accessToken");

        var baseline = send("POST", "/api/payments", token, "day25-baseline", payment("baseline-merchant"));
        assertThat(baseline.statusCode()).isEqualTo(201);
        String baselineId = JsonPath.read(baseline.body(), "$.id");
        var baselineRows = database.queryForList("select * from payments where id = ?", UUID.fromString(baselineId));
        var baselineKeys = database.queryForList("select * from idempotency_records where idempotency_key = ?", "day25-baseline");

        var created = send("POST", "/api/payments", token, "day25-apostrophe", payment("O'Reilly"));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(JsonPath.<String>read(created.body(), "$.merchantId")).isEqualTo("O'Reilly");
        String id = JsonPath.read(created.body(), "$.id");
        assertThat(database.queryForObject("select merchant_id from payments where id = ?", String.class,
                UUID.fromString(id))).isEqualTo("O'Reilly");

        var retrieved = send("GET", "/api/payments/" + id, token, null, null);
        assertThat(retrieved.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.merchantId")).isEqualTo("O'Reilly");

        var replay = send("POST", "/api/payments", token, "day25-apostrophe", payment("O'Reilly"));
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(replay.body(), "$.id")).isEqualTo(id);
        assertThat(database.queryForObject("select count(*) from payments", Long.class)).isEqualTo(2);
        assertThat(database.queryForObject("select count(*) from idempotency_records", Long.class)).isEqualTo(2);
        assertThat(database.queryForList("select * from payments where id = ?", UUID.fromString(baselineId)))
                .isEqualTo(baselineRows);
        assertThat(database.queryForList("select * from idempotency_records where idempotency_key = ?", "day25-baseline"))
                .isEqualTo(baselineKeys);
        var unchanged = send("GET", "/api/payments/" + baselineId, token, null, null);
        assertThat(unchanged.statusCode()).isEqualTo(200);
        assertThat(JsonPath.parse(unchanged.body()).jsonString()).isEqualTo(JsonPath.parse(baseline.body()).jsonString());
    }

    @Test
    void sqlLookingMerchantIsStoredLiterallyAndCannotReadAnotherCustomersPayment() throws Exception {
        var customerLogin = send("POST", "/auth/login", null, null,
                "{\"username\":\"customer-123\",\"password\":\"day25-isolated-test-password\"}");
        var otherLogin = send("POST", "/auth/login", null, null,
                "{\"username\":\"customer-789\",\"password\":\"day25-isolated-test-password\"}");
        assertThat(customerLogin.statusCode()).isEqualTo(200);
        assertThat(otherLogin.statusCode()).isEqualTo(200);
        String customer = JsonPath.read(customerLogin.body(), "$.accessToken");
        String other = JsonPath.read(otherLogin.body(), "$.accessToken");

        var otherPayment = send("POST", "/api/payments", other, "day25-other-customer", payment("private-merchant"));
        assertThat(otherPayment.statusCode()).isEqualTo(201);
        String otherId = JsonPath.read(otherPayment.body(), "$.id");

        String sqlLooking = "' OR '1'='1";
        var created = send("POST", "/api/payments", customer, "day25-sql-looking", payment(sqlLooking));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(JsonPath.<String>read(created.body(), "$.merchantId")).isEqualTo(sqlLooking);
        String id = JsonPath.read(created.body(), "$.id");
        assertThat(database.queryForObject("select merchant_id from payments where id = ?", String.class,
                UUID.fromString(id))).isEqualTo(sqlLooking);
        assertThat(database.queryForObject("select count(*) from payments", Long.class)).isEqualTo(2);
        assertThat(database.queryForObject("select count(*) from idempotency_records", Long.class)).isEqualTo(2);

        var retrieved = send("GET", "/api/payments/" + id, customer, null, null);
        assertThat(retrieved.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.merchantId")).isEqualTo(sqlLooking);
        var unauthorizedRead = send("GET", "/api/payments/" + otherId, customer, null, null);
        assertThat(unauthorizedRead.statusCode()).isEqualTo(404);
        assertThat(unauthorizedRead.body()).doesNotContain("private-merchant");

        var replay = send("POST", "/api/payments", customer, "day25-sql-looking", payment(sqlLooking));
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(replay.body(), "$.id")).isEqualTo(id);
        assertThat(database.queryForObject("select count(*) from payments", Long.class)).isEqualTo(2);
        assertThat(database.queryForObject("select count(*) from idempotency_records", Long.class)).isEqualTo(2);
    }

    @Test
    void applicationInputsTreatSqlLookingValuesAsDataAcrossRoutes() throws Exception {
        String customer = login("customer-123");
        String otherCustomer = login("customer-789");

        var injectedUsername = send("POST", "/auth/login", null, null,
                "{\"username\":\"customer-123' OR '1'='1\",\"password\":\"anything\"}");
        assertThat(injectedUsername.statusCode()).isEqualTo(401);
        var injectedPassword = send("POST", "/auth/login", null, null,
                "{\"username\":\"customer-123\",\"password\":\"' OR '1'='1\"}");
        assertThat(injectedPassword.statusCode()).isEqualTo(401);

        var foreignPayment = send("POST", "/api/payments", otherCustomer, "day25-foreign", payment("private-merchant"));
        assertThat(foreignPayment.statusCode()).isEqualTo(201);
        String foreignId = JsonPath.read(foreignPayment.body(), "$.id");

        // Invalid field formats are rejected before a payment or idempotency row is written.
        var injectedCurrency = send("POST", "/api/payments", customer, "day25-bad-currency",
                "{\"merchantId\":\"merchant\",\"amount\":10.00,\"currency\":\"USD' OR '1'='1\"}");
        assertThat(injectedCurrency.statusCode()).isEqualTo(400);
        var injectedAmount = send("POST", "/api/payments", customer, "day25-bad-amount",
                "{\"merchantId\":\"merchant\",\"amount\":\"1 OR 1=1\",\"currency\":\"USD\"}");
        assertThat(injectedAmount.statusCode()).isEqualTo(400);
        assertThat(database.queryForObject("select count(*) from payments", Long.class)).isEqualTo(1);
        assertThat(database.queryForObject("select count(*) from idempotency_records", Long.class)).isEqualTo(1);

        List<String> merchantInputs = List.of(
                "O'Reilly",
                "' OR '1'='1",
                "'; DROP TABLE payments; --",
                "\" UNION SELECT null --");
        int index = 0;
        for (String merchant : merchantInputs) {
            String key = index == 0 ? "day25-key-' OR '1'='1" : "day25-probe-" + index;
            var created = send("POST", "/api/payments", customer, key, payment(merchant));
            assertThat(created.statusCode()).as("merchant input %s: %s", merchant, created.body()).isEqualTo(201);
            assertThat(JsonPath.<String>read(created.body(), "$.merchantId")).isEqualTo(merchant);
            String id = JsonPath.read(created.body(), "$.id");
            assertThat(database.queryForObject("select merchant_id from payments where id = ?", String.class,
                    UUID.fromString(id))).isEqualTo(merchant);
            var retrieved = send("GET", "/api/payments/" + id, customer, null, null);
            assertThat(retrieved.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(retrieved.body(), "$.merchantId")).isEqualTo(merchant);
            var replay = send("POST", "/api/payments", customer, key, payment(merchant));
            assertThat(replay.statusCode()).isEqualTo(201);
            assertThat(JsonPath.<String>read(replay.body(), "$.id")).isEqualTo(id);
            index++;
        }

        var invalidIdPath = send("GET", "/api/payments/00000000%27%20OR%20%271%27=%271", customer, null, null);
        assertThat(invalidIdPath.statusCode()).isEqualTo(400);
        var cannotReadForeignPayment = send("GET", "/api/payments/" + foreignId, customer, null, null);
        assertThat(cannotReadForeignPayment.statusCode()).isEqualTo(404);
        assertThat(cannotReadForeignPayment.body()).doesNotContain("private-merchant");
        assertThat(database.queryForObject("select count(*) from payments", Long.class)).isEqualTo(5);
        assertThat(database.queryForObject("select count(*) from idempotency_records", Long.class)).isEqualTo(5);
    }

    private String payment(String merchant) {
        return "{\"merchantId\":\"" + jsonEscape(merchant) + "\",\"amount\":10.00,\"currency\":\"USD\"}";
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String login(String username) throws Exception {
        var response = send("POST", "/auth/login", null, null,
                "{\"username\":\"" + jsonEscape(username) + "\",\"password\":\"day25-isolated-test-password\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.accessToken");
    }

    private HttpResponse<String> send(String method, String path, String token, String key, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
