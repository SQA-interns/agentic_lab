# Backend image: the jar built by `./mvnw -B -ntp -DskipTests package` on the pinned JRE.
FROM eclipse-temurin:21.0.10_7-jre-alpine

RUN addgroup -S registration && adduser -S -G registration -H registration \
    && mkdir -p /app /var/lib/registration/json-copies \
    && chown -R registration:registration /var/lib/registration

COPY --chown=root:root target/registration-backend-0.1.0.jar /app/registration-backend.jar

USER registration
WORKDIR /app
EXPOSE 8080
ENV JSON_COPY_DIR=/var/lib/registration/json-copies

HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/registration-backend.jar"]
