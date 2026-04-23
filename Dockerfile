# ── Build stage ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy Gradle wrapper + build files first (layer cache)
COPY gradlew .
COPY gradle/ gradle/
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY rate-limiter-core/build.gradle.kts rate-limiter-core/
COPY rate-limiter-api/build.gradle.kts  rate-limiter-api/

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon -q || true

# Copy sources
COPY rate-limiter-core/src rate-limiter-core/src
COPY rate-limiter-api/src  rate-limiter-api/src

# Build the fat JAR (skip tests — they run in CI)
RUN ./gradlew :rate-limiter-api:bootJar --no-daemon -x test

# ── Runtime stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /build/rate-limiter-api/build/libs/rate-limiter-api.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
