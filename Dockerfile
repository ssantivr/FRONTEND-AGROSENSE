# Image of the web frontend for docker compose and any host that sets DATABASE_URL, DATABASE_USERNAME and
# DATABASE_PASSWORD. It needs the workspace's database/ folder, so build it from the workspace root:
#   docker build -f frontend/Dockerfile -t agrosense-frontend .
# (Vercel uses /Dockerfile.vercel instead, which reads Neon's variables and records a class archive.)

# ── Stage 1: Build ────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY frontend/.mvn/ .mvn/
COPY frontend/mvnw frontend/pom.xml ./
# The wrapper may arrive with Windows line endings from a Windows checkout.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY frontend/src/ src/
RUN ./mvnw package -DskipTests -B

# ── Stage 2: Run ──────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S agrosense && adduser -S agrosense -G agrosense
COPY --from=builder /app/target/*.jar app.jar
# Connection settings, schema and sample data; application.properties imports them from DATABASE_DIR.
COPY database/ /app/database/
ENV DATABASE_DIR=/app/database
ENV PORT=8080
USER agrosense
EXPOSE 8080
# /login is public and needs the database (the session table), so it says the frontend can serve pages.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/login" > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
