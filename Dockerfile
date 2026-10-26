# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-26-noble@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS build

WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
COPY src ./src
COPY config ./config
# CI runs the full test suites before building the image. Keep image builds
# focused on packaging so Docker BuildKit does not execute the suites again.
RUN --mount=type=cache,target=/root/.m2 mvn -B -Duser.timezone=UTC -DskipTests package

FROM eclipse-temurin:27-jre-alpine-3.24@sha256:0faf24c1d1e778bbc0700fc71c53b081c9432f1e51e6e41c86c63840e8b89ac1 AS runtime

LABEL org.opencontainers.image.title="SecurePay" \
      org.opencontainers.image.source="https://github.com/nicolasfarina/securepay"

# The pinned runtime image still includes vulnerable Brotli (CVE-2026-111497).
# Require the patched Alpine package; fail the image build if unavailable.
RUN apk add --no-cache 'libexpat>=2.9.0-r0' 'brotli-libs>=1.2.1-r0' \
    && addgroup -S -g 10001 spring \
    && adduser -S -u 10001 -G spring spring
WORKDIR /app
COPY --from=build --chown=10001:10001 --chmod=0444 /workspace/target/securepay-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001

EXPOSE 8080
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
