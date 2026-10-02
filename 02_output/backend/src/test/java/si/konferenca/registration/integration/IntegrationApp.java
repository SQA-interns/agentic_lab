package si.konferenca.registration.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import si.konferenca.registration.RegistrationApplication;

/**
 * The backend started for an integration test with its real settings (specification section 5),
 * PostgreSQL in a container and no mail server (the SMTP port is closed; emails may fail, D-10).
 */
final class IntegrationApp implements AutoCloseable {

  static final String ORGANIZER_USERNAME = "organizer-integration";
  static final String ORGANIZER_PASSWORD = "only-for-integration-tests-1";
  static final String VALID_EXTERNAL =
      """
      {"type":"EXTERNAL","firstName":"Ana","lastName":"Novak","email":"ana.novak@example.org",
       "organization":"Podjetje","optionIds":[],"consent":true,"captchaToken":"%s"}
      """;

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16.15-alpine");
  private static final HttpClient CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  static {
    POSTGRES.start();
  }

  private final ConfigurableApplicationContext context;
  private final String baseUrl;

  private IntegrationApp(ConfigurableApplicationContext context) {
    this.context = context;
    this.baseUrl = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
  }

  /** The settings every integration test starts from; {@code overrides} replace them. */
  static Map<String, String> settings(Map<String, String> overrides) {
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("APP_ENVIRONMENT", "test");
    settings.put("DB_URL", POSTGRES.getJdbcUrl());
    settings.put("DB_USER", POSTGRES.getUsername());
    settings.put("POSTGRES_PASSWORD", POSTGRES.getPassword());
    settings.put("SMTP_HOST", "127.0.0.1");
    settings.put("SMTP_PORT", String.valueOf(closedPort()));
    settings.put("SMTP_STARTTLS", "false");
    settings.put("ORGANIZER_EMAILS", "organizer@example.org");
    settings.put("OPTIONS_FILE", optionsFile().toString());
    settings.put("JSON_COPY_DIR", temporaryDirectory().toString());
    settings.put("RECAPTCHA_TEST_MODE", "true");
    settings.put("ORGANIZER_USERNAME", ORGANIZER_USERNAME);
    settings.put("ORGANIZER_PASSWORD", ORGANIZER_PASSWORD);
    settings.put("ORGANIZER_REQUIRE_HTTPS", "false");
    settings.put("server.port", "0");
    settings.putAll(overrides);
    return settings;
  }

  static IntegrationApp start(Map<String, String> overrides) {
    String[] args =
        settings(overrides).entrySet().stream()
            .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
            .toArray(String[]::new);
    return new IntegrationApp(new SpringApplication(RegistrationApplication.class).run(args));
  }

  private static Path optionsFile() {
    try {
      return Path.of(IntegrationApp.class.getResource("/acceptance/options-default.json").toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Path temporaryDirectory() {
    try {
      return Files.createTempDirectory("integration-json-copies");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  HttpResponse<String> get(String path, String... headers) {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
    for (int i = 0; i < headers.length; i += 2) {
      request.header(headers[i], headers[i + 1]);
    }
    return send(request);
  }

  HttpResponse<String> post(String path, String contentType, String body, String... headers) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", contentType)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    for (int i = 0; i < headers.length; i += 2) {
      request.header(headers[i], headers[i + 1]);
    }
    return send(request);
  }

  HttpResponse<String> register(String captchaToken) {
    return post("/api/registrations", "application/json", VALID_EXTERNAL.formatted(captchaToken));
  }

  private static HttpResponse<String> send(HttpRequest.Builder request) {
    try {
      return CLIENT.send(
          request.timeout(Duration.ofSeconds(60)).build(),
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** The number of stored registrations. */
  static int registrationCount() {
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT count(*) FROM registration")) {
      result.next();
      return result.getInt(1);
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void close() {
    context.close();
  }
}
