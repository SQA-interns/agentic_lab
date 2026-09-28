# ST-09: images pinned by digest (tags recorded in docs/resolved-versions.md).
# eclipse-temurin:21-jdk-noble
FROM eclipse-temurin@sha256:d0aa6704a67ac080591815bb7734dbe56171dd7a8e9b767e9989b95dd37b3085 AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml spotbugs-exclude.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline -DexcludeReactor=true || true
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -DskipITs \
    && cp target/conference-registration-backend-*.jar /app.jar

# eclipse-temurin:21-jre-noble
FROM eclipse-temurin@sha256:7edbe8532195c8222735caca437e56780e1fc7db08a4b4b4af77fe188cccc15f
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app \
    && mkdir -p /data/backups /config && chown -R app:app /data
COPY --from=build /app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/urandom"
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/readiness HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q '\"UP\"' <&3"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
