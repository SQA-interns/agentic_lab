# --- build stage: Maven via the committed wrapper ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src ./src
RUN ./mvnw -B -q package -DskipTests -Djacoco.skip=true \
    && cp target/registration-*.jar /build/app.jar

# --- runtime stage: JRE only, non-root ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app \
    && mkdir -p /data/backups /config && chown -R app:app /data
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER 10001:10001
ENV APP_BACKUP_DIR=/data/backups \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
VOLUME ["/data/backups"]
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
