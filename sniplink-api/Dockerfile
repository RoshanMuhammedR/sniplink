# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------
# The Maven image rather than ./mvnw: .mvn/wrapper/maven-wrapper.jar is
# gitignored, so the wrapper would download a Maven distribution on every cold
# build inside the container.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Resolve dependencies against the pom alone first. This layer is reused on
# every build that does not change the pom, so source-only changes skip the
# whole download.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run here on purpose: the Base62 suite is pure unit tests with no
# Postgres or Redis, so an image can never be built from failing code.
RUN mvn -B clean package

# ---- runtime --------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build --chown=spring:spring /build/target/*.jar /app/app.jar

USER spring

EXPOSE 8080

# Size the heap to the container limit, not the host's RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# busybox wget ships with alpine, so no extra package is needed. start-period
# covers JVM boot plus the first Hibernate schema check against Postgres.
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
	CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q '"UP"' || exit 1

# exec so the JVM is PID 1 and receives SIGTERM directly on `docker stop`.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
