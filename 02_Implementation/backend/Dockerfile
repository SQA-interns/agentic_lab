FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /app/data/registrations /app/config \
    && chown -R app:app /app/data
COPY target/registration-backend-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
