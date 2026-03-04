# ─── Stage 1: Build ─────────────────────────────────
FROM gradle:8.12-jdk21 AS builder
WORKDIR /app

# Copy build files first for better layer caching
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Download dependencies (cached until build files change)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src
COPY libs ./libs

# Build JAR (skip tests — they run in CI separately)
RUN gradle bootJar --no-daemon -x test

# ─── Stage 2: Runtime ───────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Security: non-root user
RUN addgroup -g 1001 -S palletpilot && \
    adduser -u 1001 -S palletpilot -G palletpilot

# Copy JAR from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Healthcheck
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Switch to non-root user
USER palletpilot

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
