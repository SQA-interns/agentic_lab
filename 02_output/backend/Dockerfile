FROM eclipse-temurin:21.0.10_7-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY target/registration-backend-0.1.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
