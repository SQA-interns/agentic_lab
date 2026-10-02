# Backend image. The jar is built beforehand with `./mvnw -B -DskipTests package`
# (tech-stack.md lists a JRE image only, so there is no build stage).
FROM eclipse-temurin:21.0.10_7-jre-alpine

# Non-root user (SB-11); it owns only the JSON copy directory.
RUN addgroup -S app \
    && adduser -S -G app app \
    && mkdir -p /data/registrations \
    && chown app:app /data/registrations

WORKDIR /app
COPY target/registration-0.1.0.jar /app/app.jar

USER app
EXPOSE 8080
VOLUME ["/data/registrations"]

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=6 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
