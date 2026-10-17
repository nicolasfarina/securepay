# Day 14 — Externalized configuration and environment profiles

Implemented on October 17, 2026.

## Lesson

Spring Boot loads common application settings from `application.yml` and overlays profile-specific settings from `application-{profile}.yml`. The `local` profile is the default and uses an in-memory H2 database. The `postgres` profile is for local PostgreSQL development. The `prod` profile requires explicit database and security values and disables interactive API documentation.

Configuration values can come from environment variables. Production credentials should be supplied by the deployment platform's secret manager, never committed in repository files. Compose now requires explicit database, demo-user and JWT values instead of silently using known demo defaults.

## Verification

- `mvn test` verifies the test suite with its explicit H2 test configuration.
- Local startup without a profile selects `local` and does not need PostgreSQL.
- The `postgres` profile requires `SECUREPAY_DATABASE_PASSWORD` and `SECUREPAY_JWT_SECRET`; its URL and username have local-development defaults that can be overridden.
- The `prod` profile requires database URL, username, password, demo password and JWT secret. Missing values prevent startup. Swagger/OpenAPI endpoints are disabled.
- Compose configuration requires explicit `SECUREPAY_DATABASE_PASSWORD`, `SECUREPAY_DEMO_PASSWORD`, and `SECUREPAY_JWT_SECRET` values.

See the profile and variable table in the [README](../README.md#run-locally).
