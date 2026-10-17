# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-26-alpine@sha256:4c85d516dd8c7e50d49a403d604e565e0dbb09a790186fe3245725d313ae7cb9 AS build

WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
COPY src ./src
COPY config ./config
RUN --mount=type=cache,target=/root/.m2 mvn -B -Duser.timezone=UTC verify

FROM eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61 AS runtime

LABEL org.opencontainers.image.title="SecurePay" \
      org.opencontainers.image.source="https://github.com/nicolasfarina/securepay"

RUN apk add --no-cache 'libexpat>=2.9.0-r0' \
    && addgroup -S -g 10001 spring \
    && adduser -S -u 10001 -G spring spring
WORKDIR /app
COPY --from=build --chown=10001:10001 --chmod=0444 /workspace/target/securepay-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001

EXPOSE 8080
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
