FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY ../pom.xml .
RUN mvn dependency:go-offline

COPY ../src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/target/HackOnLinces-0.0.1-SNAPSHOT.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]