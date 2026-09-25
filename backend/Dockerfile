# syntax=docker/dockerfile:1
#
# Multi-stage build:
#   stage 1 (build)   full JDK + Maven: compiles and packages the jar
#   stage 2 (runtime) JRE only: runs the jar
# The final image carries no compiler, no Maven, no source code and no ~/.m2 cache:
# smaller (~200 MB instead of ~700 MB) and a smaller attack surface.

# ---------- stage 1: build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy only the build definition first and download dependencies. Docker caches this layer,
# so editing Java code later doesn't re-download every dependency.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Windows checkouts can turn mvnw's line endings into CRLF, which breaks it on Linux.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests are not run here: they need Docker themselves (Testcontainers). They run with
# "./mvnw verify" on a developer machine or in CI, before an image is built.
RUN ./mvnw -B -q package -DskipTests && cp target/foodflow-backend-*.jar app.jar

# ---------- stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as an unprivileged user: a compromised app can't modify the image or act as root.
RUN addgroup -S foodflow && adduser -S foodflow -G foodflow
COPY --from=build --chown=foodflow:foodflow /workspace/app.jar app.jar
USER foodflow

# Size the heap from the container's memory limit, not the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Docker (and Compose's depends_on: service_healthy) consider the container healthy only once
# Spring Boot answers, including its database check.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
