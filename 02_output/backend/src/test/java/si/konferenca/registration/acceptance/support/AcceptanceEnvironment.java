package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
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
 * Containers and configuration shared by every acceptance test. The application is configured only
 * through the environment variables of docs/02_specification.md, section 5.
 */
public final class AcceptanceEnvironment {

  public static final String CONFERENCE_NAME = "Konferenca Acceptance 2026";
  public static final String MAIL_FROM = "registrations@konferenca.example.si";
  public static final String ORGANIZER_USERNAME = "organizer";
  public static final String ORGANIZER_PASSWORD = "organizer-acceptance-password";
  public static final List<String> ORGANIZER_EMAILS =
      List.of("organizer.one@konferenca.example.si", "organizer.two@konferenca.example.si");

  /** The options contract example, used unchanged as the options file of the shared app. */
  public static final Path OPTIONS_FILE =
      Path.of("..", "docs", "02_contracts", "conference-options.example.json")
          .toAbsolutePath()
          .normalize();

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16.15-alpine");

  private static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.1"))
          .withExposedPorts(1025, 8025)
          .withEnv("MP_ENABLE_CHAOS", "true")
          .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

  private static final Path JSON_DIR;

  static {
    POSTGRES.start();
    MAILPIT.start();
    try {
      JSON_DIR = Files.createTempDirectory("acceptance-json-copies");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private AcceptanceEnvironment() {}

  /** Configuration of the shared application under test. */
  public static Map<String, Object> properties() {
    return propertiesFor(POSTGRES.getJdbcUrl(), JSON_DIR, OPTIONS_FILE);
  }

  /** Configuration of an application against the given database, directory and options. */
  public static Map<String, Object> propertiesFor(String jdbcUrl, Path jsonDir, Path optionsFile) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("DB_URL", jdbcUrl);
    p.put("DB_USERNAME", POSTGRES.getUsername());
    p.put("POSTGRES_PASSWORD", POSTGRES.getPassword());
    p.put("SMTP_HOST", MAILPIT.getHost());
    p.put("SMTP_PORT", MAILPIT.getMappedPort(1025));
    p.put("SMTP_STARTTLS", "false");
    p.put("MAIL_FROM", MAIL_FROM);
    p.put("CONFERENCE_NAME", CONFERENCE_NAME);
    p.put("CONFERENCE_OPTIONS_FILE", optionsFile.toString());
    p.put("JSON_COPY_DIR", jsonDir.toString());
    p.put("RECAPTCHA_TEST_MODE", "true");
    p.put("ORGANIZER_USERNAME", ORGANIZER_USERNAME);
    p.put("ORGANIZER_PASSWORD", ORGANIZER_PASSWORD);
    p.put("ORGANIZER_EMAILS", String.join(",", ORGANIZER_EMAILS));
    p.put("ORGANIZER_HTTPS_ONLY", "true");
    p.put("RATE_LIMIT_REGISTRATIONS", "10000");
    p.put("RATE_LIMIT_TOKENS", "10000");
    p.put("RATE_LIMIT_EXPORTS", "10000");
    p.put("RATE_LIMIT_FORM_CONFIG", "10000");
    return p;
  }

  public static String jdbcUrl() {
    return POSTGRES.getJdbcUrl();
  }

  /** JDBC URL of another database in the same PostgreSQL container. */
  public static String jdbcUrlOf(String database) {
    return POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
  }

  public static Database database() {
    return new Database(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  public static Database databaseOf(String database) {
    return new Database(jdbcUrlOf(database), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  public static Mailpit mailpit() {
    return new Mailpit("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025));
  }

  public static JsonCopies jsonCopies() {
    return new JsonCopies(JSON_DIR);
  }

  /** SMTP endpoint of the mail catcher, for harness probes. */
  public static String smtpHost() {
    return MAILPIT.getHost();
  }

  public static int smtpPort() {
    return MAILPIT.getMappedPort(1025);
  }
}
