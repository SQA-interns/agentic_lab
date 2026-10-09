package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The local substitutes the acceptance tests run against (project/stack.md, "Environments"):
 * PostgreSQL and Mailpit containers shared by every test class, and the settings of the
 * specification (section 9) with test values.
 */
public final class TestStack {

  public static final String CONFERENCE_NAME = "Konferenca Test 2026";
  public static final String MAIL_FROM = "registration@konferenca.test";
  public static final String ORGANIZER_USERNAME = "organizer";
  public static final String ORGANIZER_PASSWORD = "organizer-test-password";
  public static final List<String> ORGANIZER_EMAILS =
      List.of("organizer.one@example.org", "organizer.two@example.org");
  public static final String CAPTCHA_TOKEN = "test-valid";
  public static final String OPTIONS_FILE = "classpath:acceptance/conference-options.json";

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16.15-alpine"));
  private static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.1"))
          .withExposedPorts(1025, 8025)
          .waitingFor(Wait.forHttp("/livez").forPort(8025));
  private static final Path COPY_DIR;

  static {
    POSTGRES.start();
    MAILPIT.start();
    COPY_DIR = newTempDirectory("registration-copies");
  }

  private TestStack() {}

  /** Settings for an application instance; entries of {@code overrides} replace defaults. */
  public static Map<String, Object> properties(Map<String, Object> overrides) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("DATABASE_URL", POSTGRES.getJdbcUrl());
    properties.put("DATABASE_USER", POSTGRES.getUsername());
    properties.put("POSTGRES_PASSWORD", POSTGRES.getPassword());
    properties.put("SMTP_HOST", MAILPIT.getHost());
    properties.put("SMTP_PORT", MAILPIT.getMappedPort(1025));
    properties.put("SMTP_TLS", "false");
    properties.put("MAIL_FROM", MAIL_FROM);
    properties.put("CONFERENCE_NAME", CONFERENCE_NAME);
    properties.put("OPTIONS_FILE", OPTIONS_FILE);
    properties.put("JSON_COPY_DIR", COPY_DIR.toString());
    properties.put("RECAPTCHA_TEST_MODE", "true");
    properties.put("ORGANIZER_USERNAME", ORGANIZER_USERNAME);
    properties.put("ORGANIZER_PASSWORD", ORGANIZER_PASSWORD);
    properties.put("ORGANIZER_EMAILS", String.join(",", ORGANIZER_EMAILS));
    properties.put("ORGANIZER_HTTPS_ONLY", "true");
    properties.put("RATE_LIMIT_REGISTRATIONS_PER_MINUTE", "100000");
    properties.put("RATE_LIMIT_EXPORTS_PER_MINUTE", "100000");
    properties.put("RATE_LIMIT_FORM_PER_MINUTE", "100000");
    properties.putAll(overrides);
    return properties;
  }

  public static void register(DynamicPropertyRegistry registry, Map<String, Object> overrides) {
    properties(overrides).forEach((key, value) -> registry.add(key, () -> value));
  }

  public static Database database() {
    return new Database(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  public static Mailpit mailpit() {
    return new Mailpit("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025));
  }

  public static Path copyDir() {
    return COPY_DIR;
  }

  public static Path newTempDirectory(String prefix) {
    try {
      return Files.createTempDirectory(prefix);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
