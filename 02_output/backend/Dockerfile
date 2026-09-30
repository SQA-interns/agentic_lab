# The jar is built on the host with ./mvnw package (README); the image only runs it.
FROM eclipse-temurin:21.0.10_7-jre-alpine
RUN addgroup -S app && adduser -S -G app app && mkdir -p /data/json && chown -R app:app /data
WORKDIR /app
COPY target/registration-backend-1.0.0.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=10 CMD wget -q -O /dev/null http://127.0.0.1:8080/api/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
