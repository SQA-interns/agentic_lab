# Runtime image only; the jar is built first with ./mvnw -B package (see README).
FROM eclipse-temurin:21.0.10_7-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app \
    && mkdir -p /data/registrations && chown app:app /data/registrations
WORKDIR /app
COPY target/registration-backend-0.1.0.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
