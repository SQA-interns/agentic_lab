# Backend image (docs/02_specification.md §12, D-15): the jar is built on the host with
# ./mvnw -B package, then runs on the pinned JRE image as a non-root user (SB-11).
FROM eclipse-temurin:21.0.10_7-jre-alpine

RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /data/registrations \
    && chown app:app /data/registrations

WORKDIR /app
COPY target/registration-backend-0.1.0.jar /app/app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
