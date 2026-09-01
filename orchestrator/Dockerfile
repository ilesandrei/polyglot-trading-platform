# ═══════════════════════════════════════════════════════════════
#  orchestrator/Dockerfile — Phase 3: Java Spring Boot gRPC Hub
#
#  Stage 1 (builder): compiles the Gradle project inside Linux,
#                     using the shared proto/ files from the repo root.
#  Stage 2 (runtime): copies only the runnable JAR — lean image.
# ═══════════════════════════════════════════════════════════════

# ── Stage 1: Build ──────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /workspace

# Copy Gradle wrapper and dependency manifests first (layer cache)
# so that `./gradlew dependencies` is only re-run when build.gradle changes.
COPY orchestrator/gradle           gradle
COPY orchestrator/gradlew          gradlew
COPY orchestrator/settings.gradle  settings.gradle
COPY orchestrator/build.gradle     build.gradle

# Make gradlew executable (important on Linux)
RUN chmod +x gradlew

# Download dependencies (cached if build.gradle unchanged)
RUN ./gradlew dependencies --no-daemon

# Copy the shared proto files (used by protobuf codegen in build.gradle)
COPY proto/ ../proto/

# Copy application source code
COPY orchestrator/src src/

# Build the fat JAR (includes all dependencies)
RUN ./gradlew bootJar --no-daemon

# ── Stage 2: Runtime ────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

# Copy only the compiled JAR from the builder stage
COPY --from=builder /workspace/build/libs/*.jar app.jar

# REST API port
EXPOSE 8080

# gRPC server port
EXPOSE 50052

# Run the Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]
