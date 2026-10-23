# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-26-noble@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS build

WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
COPY src ./src
COPY config ./config
RUN --mount=type=cache,target=/root/.m2 mvn -B -Duser.timezone=UTC verify

FROM eclipse-temurin:25-jre-alpine-3.24@sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048 AS runtime

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
