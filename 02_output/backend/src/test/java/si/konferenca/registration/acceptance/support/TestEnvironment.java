package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared black-box environment of the backend acceptance tests: PostgreSQL and Mailpit containers
 * (pinned images of project/stack.md), the conference configuration file and the JSON copy
 * directory. The backend is configured only through the settings of docs/02_specification.md §9.
 */
public final class TestEnvironment {

  public static final String ORGANIZER_USERNAME = "organizer";
  public static final String ORGANIZER_PASSWORD = "acceptance-organizer-pw-0001";
  public static final List<String> ORGANIZER_EMAILS =
      List.of("organizer.one@example.com", "organizer.two@example.com");
  public static final String CONFERENCE_NAME = "Acceptance Conference 2026";
  public static final String MAIL_FROM = "registration@acceptance.example";
  public static final String CAPTCHA_PASS = "test-pass";

  // Every test class has its own application context and connection pool.
  public static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16.15-alpine"))
          .withCommand("postgres", "-c", "max_connections=500");

  public static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.1"))
          .withExposedPorts(1025, 8025)
          .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

  public static final Path JSON_COPY_DIR;
  public static final Path CONFIG_FILE;

  static {
    POSTGRES.start();
    MAILPIT.start();
    try {
      JSON_COPY_DIR = Files.createTempDirectory("acceptance-json-copies");
      CONFIG_FILE = Files.createTempFile("acceptance-conference-config", ".json");
      Files.write(CONFIG_FILE, resource("/acceptance/conference-config.json"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private TestEnvironment() {}

  /** Settings of docs/02_specification.md §9 for the test environment. */
  public static Map<String, String> properties() {
    Map<String, String> p = new LinkedHashMap<>();
    p.put("APP_ENVIRONMENT", "test");
    p.put("DB_URL", POSTGRES.getJdbcUrl());
    p.put("DB_USERNAME", POSTGRES.getUsername());
    p.put("POSTGRES_PASSWORD", POSTGRES.getPassword());
    // Spring's own datasource names with the same values, so the application context starts even
    // before the backend maps DB_URL; the tests then fail on behaviour, not on startup.
    p.put("spring.datasource.url", POSTGRES.getJdbcUrl());
    p.put("spring.datasource.username", POSTGRES.getUsername());
    p.put("spring.datasource.password", POSTGRES.getPassword());
    p.put("SMTP_HOST", MAILPIT.getHost());
    p.put("SMTP_PORT", String.valueOf(MAILPIT.getMappedPort(1025)));
    p.put("SMTP_TLS", "false");
    p.put("MAIL_FROM", MAIL_FROM);
    p.put("CONFERENCE_NAME", CONFERENCE_NAME);
    p.put("CONFERENCE_CONFIG_FILE", CONFIG_FILE.toAbsolutePath().toString());
    p.put("JSON_COPY_DIR", JSON_COPY_DIR.toAbsolutePath().toString());
    p.put("RECAPTCHA_TEST_MODE", "true");
    p.put("ORGANIZER_USERNAME", ORGANIZER_USERNAME);
    p.put("ORGANIZER_PASSWORD", ORGANIZER_PASSWORD);
    p.put("ORGANIZER_EMAILS", String.join(",", ORGANIZER_EMAILS));
    p.put("ORGANIZER_HTTPS_ONLY", "false");
    p.put("RATE_LIMIT_REGISTRATIONS_PER_MINUTE", "100000");
    p.put("RATE_LIMIT_EXPORTS_PER_MINUTE", "100000");
    return p;
  }

  public static String mailpitUrl() {
    return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
  }

  /** A local port with nothing listening on it. */
  public static int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static byte[] resource(String name) {
    try (InputStream in = TestEnvironment.class.getResourceAsStream(name)) {
      if (in == null) {
        throw new IllegalStateException("missing test resource " + name);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
