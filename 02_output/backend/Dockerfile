# Backend runtime image (architecture.md: eclipse-temurin base, non-root; SB-11).
# Build the jar first on the host: ./mvnw -B package
FROM eclipse-temurin:21.0.10_7-jre-alpine

RUN addgroup -S -g 10001 app \
    && adduser -S -u 10001 -G app -H -h /app app \
    && mkdir -p /app/config /data/registrations \
    && chown -R app:app /data

WORKDIR /app
COPY --chown=root:root target/registration-backend-1.0.0.jar /app/app.jar
COPY --chown=root:root config/options.json /app/config/options.json

ENV CONFERENCE_OPTIONS_FILE=/app/config/options.json \
    JSON_COPY_DIR=/data/registrations \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.io.tmpdir=/tmp"

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
