package si.konferenca.registration.acceptance.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared black-box test environment: one PostgreSQL and one Mailpit container for the whole test
 * run (tech-stack images), a fresh database, JSON copy directory and organizer address per started
 * application instance. Configuration is passed only through the environment names of the
 * specification, section 5.
 */
public final class AcceptanceEnvironment {

  public static final String ORGANIZER_USERNAME = "organizer";
  public static final String ORGANIZER_PASSWORD = "acceptance-only-organizer-pw";
  public static final String CONFERENCE_NAME = "Konferenca Test 2026";
  public static final String MAIL_FROM = "registration@konferenca.test";
  public static final String OPTIONS_FILE = "classpath:acceptance/conference-options.yaml";

  private static final AtomicInteger COUNTER = new AtomicInteger();

  @SuppressWarnings("resource")
  public static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16.15-alpine"));

  @SuppressWarnings("resource")
  public static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.1"))
          .withExposedPorts(1025, 8025)
          .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

  static {
    POSTGRES.start();
    MAILPIT.start();
  }

  private AcceptanceEnvironment() {}

  /** Creates an empty database and returns its JDBC URL. */
  public static String newDatabase() {
    String name = "reg_" + ProcessHandle.current().pid() + "_" + COUNTER.incrementAndGet();
    try (Connection c =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement s = c.createStatement()) {
      s.execute("CREATE DATABASE " + name);
    } catch (Exception e) {
      throw new IllegalStateException("cannot create test database", e);
    }
    return "jdbc:postgresql://"
        + POSTGRES.getHost()
        + ":"
        + POSTGRES.getMappedPort(5432)
        + "/"
        + name;
  }

  /** Default configuration of a test application instance, all names from spec section 5. */
  public static Map<String, String> defaultConfiguration() {
    int n = COUNTER.incrementAndGet();
    Path jsonDir;
    try {
      jsonDir = Files.createTempDirectory("json-copies-");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    Map<String, String> p = new LinkedHashMap<>();
    p.put("spring.profiles.active", "test");
    p.put("server.port", "0");
    p.put("DB_URL", newDatabase());
    p.put("DB_USERNAME", POSTGRES.getUsername());
    p.put("POSTGRES_PASSWORD", POSTGRES.getPassword());
    p.put("SMTP_HOST", MAILPIT.getHost());
    p.put("SMTP_PORT", String.valueOf(MAILPIT.getMappedPort(1025)));
    p.put("SMTP_STARTTLS", "false");
    p.put("MAIL_FROM", MAIL_FROM);
    p.put("CONFERENCE_NAME", CONFERENCE_NAME);
    p.put("OPTIONS_FILE", OPTIONS_FILE);
    p.put("JSON_COPY_DIR", jsonDir.toString());
    p.put("RECAPTCHA_TEST_MODE", "true");
    p.put("RECAPTCHA_SITE_KEY", "");
    p.put("RECAPTCHA_SECRET_KEY", "");
    p.put("ORGANIZER_USERNAME", ORGANIZER_USERNAME);
    p.put("ORGANIZER_PASSWORD", ORGANIZER_PASSWORD);
    p.put(
        "ORGANIZER_EMAILS", "organizer-" + n + "@konferenca.test,second-" + n + "@konferenca.test");
    p.put("ORGANIZER_HTTPS_ONLY", "true");
    p.put("CORS_ALLOWED_ORIGIN", "");
    p.put("RATE_LIMIT_REGISTRATION_PER_MINUTE", "10000");
    p.put("RATE_LIMIT_EXPORT_PER_MINUTE", "10000");
    p.put("RATE_LIMIT_OPTIONS_PER_MINUTE", "10000");
    p.put("MAX_REQUEST_BYTES", "16384");
    // Standard Spring keys with the same values, so that the harness does not depend on how the
    // application maps the environment names above.
    p.put("spring.datasource.url", p.get("DB_URL"));
    p.put("spring.datasource.username", p.get("DB_USERNAME"));
    p.put("spring.datasource.password", p.get("POSTGRES_PASSWORD"));
    p.put("spring.datasource.hikari.connection-timeout", "3000");
    p.put("spring.mail.host", p.get("SMTP_HOST"));
    p.put("spring.mail.port", p.get("SMTP_PORT"));
    return p;
  }

  public static Mailpit mailpit() {
    return new Mailpit("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025));
  }
}
