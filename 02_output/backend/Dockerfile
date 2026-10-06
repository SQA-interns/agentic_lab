# Build stage: same pinned JDK release as the runtime (D-19).
FROM eclipse-temurin:21.0.10_7-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml spotbugs-exclude.xml dependency-check-suppressions.xml ./
COPY src/main src/main
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -q -DskipTests -Djacoco.skip package \
    && cp target/registration-backend-1.0.0.jar /app.jar

# Runtime: JRE only, non-root (SB-11).
FROM eclipse-temurin:21.0.10_7-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app \
    && mkdir -p /data/json-copies && chown app:app /data/json-copies
COPY --from=build /app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.io.tmpdir=/tmp", "-jar", "/app/app.jar"]
