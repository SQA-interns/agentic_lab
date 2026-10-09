FROM eclipse-temurin:21.0.10_7-jre-alpine
# Non-root user (SB-11); the JSON copy directory belongs to it, so a new named volume does too.
RUN addgroup -S app && adduser -S app -G app \
  && mkdir -p /data/registrations && chown app:app /data/registrations
WORKDIR /app
COPY target/registration-backend-0.1.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
