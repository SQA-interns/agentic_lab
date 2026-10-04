# Backend image: the jar is built on the host first (./mvnw -B -DskipTests package); no JDK or
# Maven build image is listed in tech-stack.md. Runs as a non-root user (SB-11).
FROM eclipse-temurin:21.0.10_7-jre-alpine

RUN addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app \
    && mkdir -p /data/json-copies \
    && chown app:app /data/json-copies

WORKDIR /app
COPY target/registration-backend-0.1.0.jar /app/app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
