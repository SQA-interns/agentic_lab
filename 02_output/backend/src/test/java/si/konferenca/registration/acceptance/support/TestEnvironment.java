package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared local substitutes for the test environment (environments.md, "test"): one PostgreSQL
 * container and one Mailpit container for the whole test run, and the settings every acceptance
 * context starts from (configuration.md).
 */
public final class TestEnvironment {

  public static final String ORGANIZER_USERNAME = "organizer-test";
  public static final String ORGANIZER_PASSWORD = "test-only-organizer-password-1";
  public static final String ORGANIZER_EMAIL_1 = "organizer1@organizers.test";
  public static final String ORGANIZER_EMAIL_2 = "organizer2@organizers.test";
  public static final String CONFERENCE_NAME = "Konferenca Test 2026";
  public static final String TEST_MODE_TOKEN = "test-mode-pass";

  public static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16.15-alpine")
          .withDatabaseName("registration")
          .withUsername("registration")
          .withPassword("test-only-db-password");

  public static final GenericContainer<?> MAILPIT =
      new GenericContainer<>("axllent/mailpit:v1.31.1")
          .withExposedPorts(1025, 8025)
          .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

  private static Path defaultJsonDir;

  static {
    POSTGRES.start();
    MAILPIT.start();
  }

  private TestEnvironment() {}

  /** Settings shared by every acceptance context; callers add or replace entries. */
  public static Map<String, String> baseProperties() {
    Map<String, String> p = new LinkedHashMap<>();
    p.put("spring.datasource.url", POSTGRES.getJdbcUrl());
    p.put("spring.datasource.username", POSTGRES.getUsername());
    p.put("spring.datasource.password", POSTGRES.getPassword());
    p.put("spring.datasource.hikari.maximum-pool-size", "3");
    p.put("spring.mail.host", MAILPIT.getHost());
    p.put("spring.mail.port", String.valueOf(MAILPIT.getMappedPort(1025)));
    p.put("app.mail.from", "registration@conference.test");
    p.put("app.conference-name", CONFERENCE_NAME);
    p.put("app.options-file", resource("acceptance/conference-options.json").toUri().toString());
    p.put("app.json-copy-dir", defaultJsonDir().toString());
    p.put("app.recaptcha.test-mode", "true");
    p.put("app.organizer.username", ORGANIZER_USERNAME);
    p.put("app.organizer.password", ORGANIZER_PASSWORD);
    p.put("app.organizer.emails", ORGANIZER_EMAIL_1 + "," + ORGANIZER_EMAIL_2);
    p.put("app.organizer.https-only", "false");
    p.put("app.rate-limit.registration-per-minute", "1000");
    p.put("app.rate-limit.export-per-minute", "1000");
    p.put("app.rate-limit.read-per-minute", "1000");
    return p;
  }

  public static synchronized Path defaultJsonDir() {
    if (defaultJsonDir == null) {
      defaultJsonDir = newTempDir("json-copies");
    }
    return defaultJsonDir;
  }

  public static Path newTempDir(String prefix) {
    try {
      return Files.createTempDirectory(prefix);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Path resource(String name) {
    try {
      return Path.of(TestEnvironment.class.getClassLoader().getResource(name).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String mailpitApiUrl() {
    return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
  }
}
