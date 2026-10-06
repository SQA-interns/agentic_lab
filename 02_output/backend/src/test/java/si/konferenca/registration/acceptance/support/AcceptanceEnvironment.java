package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared test environment: one PostgreSQL and one Mailpit container for all acceptance tests, a
 * JSON copy directory and the configuration of `docs/02_specification.md` section 4.
 */
public final class AcceptanceEnvironment {

  public static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16.15-alpine");

  @SuppressWarnings("resource")
  public static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.1"))
          .withExposedPorts(1025, 8025)
          .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

  public static final String CONFERENCE_NAME = "Konferenca 2026";
  public static final String MAIL_FROM = "registration@konferenca.test";
  public static final String ORGANIZER_USERNAME = "organizer";
  public static final String ORGANIZER_PASSWORD = "acceptance-" + UUID.randomUUID();
  public static final List<String> ORGANIZER_EMAILS =
      List.of("organizer.one@konferenca.test", "organizer.two@konferenca.test");
  public static final String TEST_MODE_TOKEN = "test-mode-pass";
  public static final String CONSENT_ID = "data-processing";

  public static final Path WORK_DIR;
  public static final Path JSON_COPY_DIR;
  public static final Path CONFERENCE_CONFIG;

  static {
    POSTGRES.start();
    MAILPIT.start();
    try {
      WORK_DIR = Files.createTempDirectory("registration-acceptance-");
      JSON_COPY_DIR = Files.createDirectories(WORK_DIR.resolve("json-copies"));
      CONFERENCE_CONFIG = WORK_DIR.resolve("conference-config.json");
      writeDefaultConferenceConfig(CONFERENCE_CONFIG);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private AcceptanceEnvironment() {}

  /** Copies the conference configuration contract example (`conference-config.json`) to target. */
  public static void writeDefaultConferenceConfig(Path target) throws IOException {
    try (InputStream in =
        AcceptanceEnvironment.class.getResourceAsStream("/acceptance/conference-config.json")) {
      if (in == null) {
        throw new IOException("missing test resource acceptance/conference-config.json");
      }
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** Settings of the shared acceptance context: test environment, test-mode anti-automation. */
  public static Map<String, String> baseSettings() {
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("APP_ENVIRONMENT", "test");
    settings.put("DB_URL", POSTGRES.getJdbcUrl());
    settings.put("DB_USERNAME", POSTGRES.getUsername());
    settings.put("POSTGRES_PASSWORD", POSTGRES.getPassword());
    settings.put("MAIL_HOST", MAILPIT.getHost());
    settings.put("MAIL_PORT", String.valueOf(MAILPIT.getMappedPort(1025)));
    settings.put("MAIL_STARTTLS", "false");
    settings.put("MAIL_FROM", MAIL_FROM);
    settings.put("CONFERENCE_NAME", CONFERENCE_NAME);
    settings.put("CONFERENCE_CONFIG_FILE", CONFERENCE_CONFIG.toString());
    settings.put("JSON_COPY_DIR", JSON_COPY_DIR.toString());
    settings.put("RECAPTCHA_TEST_MODE", "true");
    settings.put("ORGANIZER_USERNAME", ORGANIZER_USERNAME);
    settings.put("ORGANIZER_PASSWORD", ORGANIZER_PASSWORD);
    settings.put("ORGANIZER_EMAILS", String.join(",", ORGANIZER_EMAILS));
    settings.put("ORGANIZER_HTTPS_ONLY", "false");
    settings.put("RATE_LIMIT_REGISTRATIONS", "100000");
    settings.put("RATE_LIMIT_EXPORTS", "100000");
    settings.put("RATE_LIMIT_CONFIG", "100000");
    return settings;
  }

  /** A local TCP port with nothing listening on it (used to make SMTP unreachable). */
  public static int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static String mailpitApiUrl() {
    return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
  }
}
