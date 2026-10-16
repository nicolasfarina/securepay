# syntax=docker/dockerfile:1
FROM maven:3.9.16-eclipse-temurin-25-alpine@sha256:781f1da87cd3d142e66e47cc149c4582a652fb7f574648206d1e956170c0dc90 AS build

WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -Duser.timezone=UTC verify

FROM eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61 AS runtime

LABEL org.opencontainers.image.title="SecurePay" \
      org.opencontainers.image.source="https://github.com/nicolasfarina/securepay"

RUN addgroup -S -g 10001 spring && adduser -S -u 10001 -G spring spring
WORKDIR /app
COPY --from=build --chown=10001:10001 --chmod=0444 /workspace/target/securepay-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001

EXPOSE 8080
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
