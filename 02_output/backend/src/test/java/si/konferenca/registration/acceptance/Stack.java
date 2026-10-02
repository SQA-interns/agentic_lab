package si.konferenca.registration.acceptance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import si.konferenca.registration.RegistrationApplication;

/**
 * The environment of the acceptance tests: PostgreSQL and a mail catcher in containers, and the
 * backend started in-process through its entry point and configured only through the settings of
 * the specification (section 5). Tests talk to it over HTTP, SQL, the file system and the mail
 * catcher API, never through its classes.
 */
final class Stack {

  static final String ORGANIZER_USERNAME = "organizer-acceptance";
  static final String ORGANIZER_PASSWORD = "only-for-acceptance-tests-1";
  static final List<String> ORGANIZER_EMAILS =
      List.of("organizer.one@example.org", "organizer.two@example.org");
  static final String MAIL_FROM = "prijave@konferenca.example";
  static final String CONFERENCE_NAME = "Testna konferenca 2027";
  static final String CONSENT_TEXT =
      "Soglašam z obdelavo osebnih podatkov za namen prijave (testno besedilo).";
  static final String UNDEFINED_TABLE = "42P01";

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16.15-alpine");
  private static final GenericContainer<?> MAILPIT =
      new GenericContainer<>("axllent/mailpit:v1.31.1")
          .withExposedPorts(1025, 8025)
          .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

  private static App defaultApp;

  static {
    POSTGRES.start();
    MAILPIT.start();
  }

  private Stack() {}

  /** A running backend instance. */
  record App(ConfigurableApplicationContext context, String baseUrl, Path jsonCopyDir)
      implements AutoCloseable {
    @Override
    public void close() {
      context.close();
    }
  }

  /** The shared instance with the default test configuration. */
  static synchronized App app() {
    if (defaultApp == null) {
      defaultApp = startApp(Map.of());
    }
    return defaultApp;
  }

  /** Starts another instance; {@code overrides} replace settings of the default configuration. */
  static App startApp(Map<String, String> overrides) {
    Map<String, String> settings = new LinkedHashMap<>(defaultSettings());
    settings.putAll(overrides);
    String[] args =
        settings.entrySet().stream()
            .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
            .toArray(String[]::new);
    ConfigurableApplicationContext context =
        new SpringApplication(RegistrationApplication.class).run(args);
    String port = context.getEnvironment().getProperty("local.server.port");
    return new App(context, "http://127.0.0.1:" + port, Path.of(settings.get("JSON_COPY_DIR")));
  }

  private static Map<String, String> defaultSettings() {
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("APP_ENVIRONMENT", "test");
    settings.put("DB_URL", POSTGRES.getJdbcUrl());
    settings.put("DB_USER", POSTGRES.getUsername());
    settings.put("POSTGRES_PASSWORD", POSTGRES.getPassword());
    settings.put("SMTP_HOST", MAILPIT.getHost());
    settings.put("SMTP_PORT", String.valueOf(MAILPIT.getMappedPort(1025)));
    settings.put("SMTP_STARTTLS", "false");
    settings.put("MAIL_FROM", MAIL_FROM);
    settings.put("CONFERENCE_NAME", CONFERENCE_NAME);
    settings.put("ORGANIZER_EMAILS", String.join(",", ORGANIZER_EMAILS));
    settings.put("OPTIONS_FILE", resource("/acceptance/options-default.json").toString());
    settings.put("CONSENT_TEXT", CONSENT_TEXT);
    settings.put("JSON_COPY_DIR", newDirectory("json-copies").toString());
    settings.put("RECAPTCHA_TEST_MODE", "true");
    settings.put("ORGANIZER_USERNAME", ORGANIZER_USERNAME);
    settings.put("ORGANIZER_PASSWORD", ORGANIZER_PASSWORD);
    settings.put("ORGANIZER_REQUIRE_HTTPS", "false");
    settings.put("RATE_LIMIT_REGISTRATION", "100000");
    settings.put("RATE_LIMIT_EXPORT", "100000");
    settings.put("RATE_LIMIT_READ", "100000");
    // Framework settings: a free port, and the data source for a backend that does not yet map
    // DB_URL, DB_USER and POSTGRES_PASSWORD itself.
    settings.put("server.port", "0");
    settings.put("spring.datasource.url", POSTGRES.getJdbcUrl());
    settings.put("spring.datasource.username", POSTGRES.getUsername());
    settings.put("spring.datasource.password", POSTGRES.getPassword());
    return settings;
  }

  static Path resource(String name) {
    try {
      return Path.of(Stack.class.getResource(name).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  static Path newDirectory(String prefix) {
    try {
      return Files.createTempDirectory(prefix);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A local port on which nothing listens. */
  static int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String mailApiUrl() {
    return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
  }

  /** Empties the database tables, the JSON copies of the shared instance and the mail catcher. */
  static void reset() {
    try {
      execute("TRUNCATE TABLE registration CASCADE");
    } catch (IllegalStateException e) {
      if (!(e.getCause() instanceof SQLException sql)
          || !UNDEFINED_TABLE.equals(sql.getSQLState())) {
        throw e;
      }
    }
    for (Path file : jsonCopies(app())) {
      try {
        Files.delete(file);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    Mailbox.clear();
  }

  static List<Path> jsonCopies(App app) {
    if (!Files.isDirectory(app.jsonCopyDir())) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(app.jsonCopyDir())) {
      return files.sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Connection connect() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  static void execute(String sql) {
    try (Connection connection = connect();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Rows of a query, column name to value. A table that does not exist has no rows. */
  static List<Map<String, Object>> rows(String sql, Object... parameters) {
    try (Connection connection = connect();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < parameters.length; i++) {
        statement.setObject(i + 1, parameters[i]);
      }
      try (ResultSet result = statement.executeQuery()) {
        ResultSetMetaData meta = result.getMetaData();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (result.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int column = 1; column <= meta.getColumnCount(); column++) {
            row.put(meta.getColumnLabel(column), result.getObject(column));
          }
          rows.add(row);
        }
        return rows;
      }
    } catch (SQLException e) {
      if (UNDEFINED_TABLE.equals(e.getSQLState())) {
        return List.of();
      }
      throw new IllegalStateException(e);
    }
  }

  static int registrationCount() {
    return rows("SELECT id FROM registration").size();
  }
}
