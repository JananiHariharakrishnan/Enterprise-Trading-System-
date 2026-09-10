# Dockerfile for Auth Stub (Sprint 6)
#
# Lightweight Node.js service for JWT token generation
# Uses Alpine for minimal image size
#
# Build context: repository root

FROM node:24-alpine

# Create non-root user
RUN addgroup -g 1001 appuser && \
    adduser -D -u 1001 -G appuser appuser

WORKDIR /app

# Copy package files first (dependency layer, cached when source changes)
COPY auth-stub/package*.json ./

# Install production dependencies only
RUN npm ci --omit=dev && \
    npm cache clean --force

# Copy application code
COPY auth-stub/ .

# Set ownership
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Health check: test the /login endpoint
HEALTHCHECK --interval=5s --timeout=3s --retries=3 --start-period=5s \
    CMD wget --no-verbose --tries=1 --spider http://localhost:4000/login || exit 1

EXPOSE 4000

CMD ["npm", "start"]

