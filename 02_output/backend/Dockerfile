# Runtime image only; build the jar first with ./mvnw -B -DskipTests package (no JDK image is pinned).
FROM eclipse-temurin:21.0.10_7-jre-alpine
RUN addgroup -S app && adduser -S -G app -H app \
    && mkdir -p /app /data/json-copies \
    && chown app:app /data/json-copies
WORKDIR /app
COPY target/registration-backend-0.1.0.jar /app/app.jar
# Non-root (SB-11); the named volume on /data/json-copies inherits this ownership.
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
