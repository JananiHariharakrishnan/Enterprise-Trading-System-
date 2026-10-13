# Multi-stage Dockerfile for Trade API (Sprint 6)
#
# Build context: repository root
# This allows copying both Backend and trade-api sources
#
# Stage 1: Build the jar with Maven and JDK
# Stage 2: Runtime with minimal Java-only image, no root user

# ============================================================================
# STAGE 1: Build
# ============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy trade-api source
COPY trade-api/ ./trade-api/

# Build trade-api jar
WORKDIR /build/trade-api
RUN mvn clean package -q -DskipTests && \
    cp ./target/trading-trade-api.jar /app.jar

# ============================================================================
# STAGE 2: Runtime
# ============================================================================
# Minimal Java runtime image, no Maven or build tools
FROM eclipse-temurin:21-jre-alpine

# Create non-root user for running the application
RUN addgroup -g 1001 appuser && \
    adduser -D -u 1001 -G appuser appuser

WORKDIR /app

# Copy only the compiled jar from the build stage
COPY --from=builder /app.jar .

# Set ownership to appuser
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Health check: curl to /health endpoint
HEALTHCHECK --interval=5s --timeout=3s --retries=3 --start-period=10s \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

EXPOSE 8080

# Run the application with explicit environment variable substitution
# JWT_SECRET must be provided at runtime via docker-compose or docker run -e
ENTRYPOINT ["java", "-jar", "app.jar"]

