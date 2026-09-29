package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared local substitutes for the test environment (environments.md): PostgreSQL and Mailpit
 * containers, started once per JVM, plus the default configuration of the backend under test
 * (specification section 3).
 */
public final class TestInfrastructure {

  public static final String ORGANIZER_USERNAME = "organizer";
  public static final String ORGANIZER_PASSWORD = "test-organizer-password-0123";
  public static final String ORGANIZER_EMAIL_1 = "org1@konferenca.test";
  public static final String ORGANIZER_EMAIL_2 = "org2@konferenca.test";
  public static final String CONFERENCE_NAME = "Konferenca 2026";
  public static final String TEST_MODE_TOKEN = "test-mode-token";

  public static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16.15-alpine"))
          .withDatabaseName("registration")
          .withUsername("registration")
          .withPassword("registration-test-password");

  @SuppressWarnings("resource")
  public static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.1"))
          .withEnv("MP_ENABLE_CHAOS", "true")
          .withExposedPorts(1025, 8025)
          .waitingFor(Wait.forHttp("/livez").forPort(8025));

  private static final Path SHARED_JSON_COPY_DIR;
  private static final Path SHARED_OPTIONS_FILE;

  static {
    POSTGRES.start();
    MAILPIT.start();
    SHARED_JSON_COPY_DIR = newTempDir("json-copies");
    SHARED_OPTIONS_FILE = writeOptionsFile(Fixtures.OPTIONS_CONFIG);
  }

  private TestInfrastructure() {}

  public static Path sharedJsonCopyDir() {
    return SHARED_JSON_COPY_DIR;
  }

  public static Mailpit mailpit() {
    return new Mailpit("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025));
  }

  public static Db db() {
    return new Db(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  /** Default backend configuration for the test environment (specification section 3). */
  public static Map<String, Object> defaultProperties() {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("spring.datasource.url", POSTGRES.getJdbcUrl());
    p.put("spring.datasource.username", POSTGRES.getUsername());
    p.put("spring.datasource.password", POSTGRES.getPassword());
    p.put("spring.mail.host", MAILPIT.getHost());
    p.put("spring.mail.port", MAILPIT.getMappedPort(1025));
    p.put("app.mail.starttls", "false");
    p.put("app.mail.from", "registration@konferenca.test");
    p.put("app.mail.retry-interval", "PT1S");
    p.put("app.mail.max-attempts", "30");
    p.put("app.conference-name", CONFERENCE_NAME);
    p.put("app.options-file", SHARED_OPTIONS_FILE.toString());
    p.put("app.json-copy-dir", SHARED_JSON_COPY_DIR.toString());
    p.put("app.organizer.username", ORGANIZER_USERNAME);
    p.put("app.organizer.password", ORGANIZER_PASSWORD);
    p.put("app.organizer.emails", ORGANIZER_EMAIL_1 + "," + ORGANIZER_EMAIL_2);
    p.put("app.organizer.https-only", "true");
    p.put("app.recaptcha.test-mode", "true");
    p.put("app.recaptcha.site-key", "");
    p.put("app.recaptcha.secret-key", "");
    p.put("app.environment", "test");
    p.put("app.rate-limit.registration-per-10-min", "100000");
    p.put("app.rate-limit.export-per-min", "100000");
    p.put("app.max-request-bytes", "16384");
    return p;
  }

  /** Registers the default configuration, then the overrides, into a Spring test context. */
  public static void register(DynamicPropertyRegistry registry, Map<String, Object> overrides) {
    Map<String, Object> all = defaultProperties();
    all.putAll(overrides);
    all.forEach((key, value) -> registry.add(key, () -> value));
  }

  public static Path newTempDir(String prefix) {
    try {
      return Files.createTempDirectory(prefix);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Path writeOptionsFile(String json) {
    try {
      Path file = Files.createTempFile("options", ".json");
      Files.writeString(file, json, StandardCharsets.UTF_8);
      return file;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
