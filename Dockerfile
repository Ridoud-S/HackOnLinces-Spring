FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup -u 1000
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

COPY --from=builder --chown=appuser:appgroup /build/target/*.jar app.jar
COPY --chown=appuser:appgroup docker/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

USER appuser

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]