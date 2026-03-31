# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Descargar dependencias primero (cache layer)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Compilar
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Crear directorio de uploads con permisos correctos
RUN mkdir -p /app/uploads

# Usuario no-root (root variant — UID 1000)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup -u 1000
RUN chown -R appuser:appgroup /app

COPY --from=builder --chown=appuser:appgroup \
     /build/target/HackOnLinces-0.0.1-SNAPSHOT.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]