package com.securepay.support;

import org.springframework.test.context.DynamicPropertyRegistry;

/** Explicit configuration for a disposable database; never silently falls back to H2. */
public final class PostgresTestDatabase {
    private PostgresTestDatabase() { }

    public static void configure(DynamicPropertyRegistry properties) {
        String url = required("SECUREPAY_TEST_POSTGRES_URL");
        if (!url.matches("jdbc:postgresql://[^/]+/securepay_test")) {
            throw new IllegalArgumentException("Use a disposable PostgreSQL database named securepay_test (no URL parameters)");
        }
        String username = required("SECUREPAY_TEST_POSTGRES_USERNAME");
        String password = required("SECUREPAY_TEST_POSTGRES_PASSWORD");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> username);
        properties.add("spring.datasource.password", () -> password);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
        return value;
    }
}
