# syntax=docker/dockerfile:1.7

# ---------------------------------------------------------------------------
# Stage 1: Build
# ---------------------------------------------------------------------------
# Using the Maven image only to compile — it never ships in the final image.
# This keeps the runtime image small and free of build tooling (smaller
# attack surface, faster pulls, no leaked source/build secrets).
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy only the POM first so Docker can cache the dependency-download layer.
# As long as pom.xml is unchanged, `mvn dependency:go-offline` is skipped on
# rebuilds even when application source changes — this is the single
# biggest lever for fast CI build times on a project this size.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2: Runtime
# ---------------------------------------------------------------------------
# jre (not jdk) — we only need to run bytecode, not compile it.
FROM eclipse-temurin:21-jre-jammy AS runtime

# curl is not present in the base JRE image; the HEALTHCHECK below needs it.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as a dedicated non-root user. A container compromise should not hand
# an attacker root inside the container.
RUN groupadd --system shortx && useradd --system --gid shortx --shell /usr/sbin/nologin shortx

WORKDIR /app

COPY --from=build /workspace/target/shortx.jar app.jar

# Directory for anything the app writes at runtime (e.g. GeoIP DB download
# cache); owned by the non-root user, not world-writable.
RUN mkdir -p /app/data && chown -R shortx:shortx /app

USER shortx

EXPOSE 8080

# Container-level health check backed by Spring Actuator, independent of
# whatever orchestrator (Compose, k8s) is driving the container — useful
# even when running the image standalone with plain `docker run`.
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=3 \
    CMD ["curl", "-f", "http://localhost:8080/actuator/health"] || exit 1

# Container memory limits should be set by the orchestrator (Compose deploy
# limits / k8s resources.limits). MaxRAMPercentage lets the JVM heap scale
# to whatever that limit is instead of hardcoding -Xmx, so the same image
# behaves correctly whether it's given 512Mi or 4Gi.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC", "-jar", "app.jar"]
