package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import si.konferenca.registration.RegistrationApplication;

/**
 * Black-box test stack: PostgreSQL and Mailpit containers, a mocked reCAPTCHA verification
 * endpoint, and backend instances configured only through the environment settings of the
 * specification (section 9). Tests talk to the backend over HTTP only.
 */
public final class AcceptanceStack {

  public static final String CONFERENCE_NAME = "Acceptance Konferenca";
  public static final String ORGANIZER_USERNAME = "organizer";
  public static final String ORGANIZER_PASSWORD = "acceptance-organizer-secret-42";
  public static final List<String> ORGANIZER_EMAILS =
      List.of("org1@konferenca.test", "org2@konferenca.test");

  /** Options used by the tests; the workshop limit is 1 so that AC-001-11 can exceed it. */
  public static final String OPTIONS_JSON =
      """
      {
        "categories": { "WORKSHOP": 1, "EVENT": 3, "MEAL": 3, "OTHER": 2 },
        "options": [
          { "id": "ws-testing", "name": "Delavnica: testiranje programske opreme", "category": "WORKSHOP", "active": true },
          { "id": "ws-security", "name": "Workshop: application security", "category": "WORKSHOP", "active": true, "registrationTypes": ["EXTERNAL"] },
          { "id": "ws-legacy", "name": "Workshop: retired topic", "category": "WORKSHOP", "active": false },
          { "id": "ev-reception", "name": "Welcome reception", "category": "EVENT", "active": true },
          { "id": "ev-career-fair", "name": "Career fair", "category": "EVENT", "active": true, "registrationTypes": ["STUDENT"] },
          { "id": "meal-lunch-day1", "name": "Lunch, day 1", "category": "MEAL", "active": true },
          { "id": "meal-dinner", "name": "Conference dinner (vegetarian option)", "category": "MEAL", "active": true },
          { "id": "other-city-tour", "name": "Ljubljana city tour", "category": "OTHER", "active": true }
        ],
        "consents": [
          { "id": "data-processing", "text": "I agree that my personal data is processed for registering me for the conference and organising my attendance.", "mandatory": true }
        ]
      }
      """;

  private static PostgreSQLContainer postgres;
  private static GenericContainer<?> mailpit;
  private static RecaptchaMock recaptcha;
  private static Path workDir;
  private static Path optionsFile;
  private static Path jsonCopyDir;
  private static RunningApp shared;

  private AcceptanceStack() {}

  /** The backend instance shared by all tests that need no special settings. */
  public static synchronized RunningApp shared() {
    ensureInfrastructure();
    if (shared == null) {
      shared = start(Map.of());
    }
    return shared;
  }

  /** Starts a separate backend instance with the default settings plus the given overrides. */
  public static synchronized RunningApp start(Map<String, String> overrides) {
    ensureInfrastructure();
    Map<String, String> settings = new LinkedHashMap<>(defaultSettings());
    settings.putAll(overrides);
    List<String> args = new ArrayList<>();
    settings.forEach((key, value) -> args.add("--" + key + "=" + value));
    ConfigurableApplicationContext context =
        new SpringApplicationBuilder(RegistrationApplication.class)
            .run(args.toArray(String[]::new));
    int port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    return new RunningApp(context, "http://127.0.0.1:" + port);
  }

  public static Map<String, String> defaultSettings() {
    Map<String, String> s = new LinkedHashMap<>();
    s.put("server.port", "0");
    s.put("APP_ENVIRONMENT", "test");
    s.put("DATABASE_URL", postgres.getJdbcUrl());
    s.put("DATABASE_USER", postgres.getUsername());
    s.put("POSTGRES_PASSWORD", postgres.getPassword());
    s.put("SMTP_HOST", mailpit.getHost());
    s.put("SMTP_PORT", String.valueOf(mailpit.getMappedPort(1025)));
    s.put("SMTP_STARTTLS", "false");
    s.put("SMTP_USERNAME", "");
    s.put("SMTP_PASSWORD", "");
    s.put("MAIL_FROM", "registration@konferenca.test");
    s.put("CONFERENCE_NAME", CONFERENCE_NAME);
    s.put("CONFERENCE_OPTIONS_FILE", optionsFile.toString());
    s.put("JSON_COPY_DIR", jsonCopyDir.toString());
    s.put("RECAPTCHA_TEST_MODE", "false");
    s.put("RECAPTCHA_SITE_KEY", RecaptchaMock.SITE_KEY);
    s.put("RECAPTCHA_SECRET_KEY", RecaptchaMock.SECRET_KEY);
    s.put("RECAPTCHA_VERIFY_URL", recaptcha.verifyUrl());
    s.put("ORGANIZER_USERNAME", ORGANIZER_USERNAME);
    s.put("ORGANIZER_PASSWORD", ORGANIZER_PASSWORD);
    s.put("ORGANIZER_EMAILS", String.join(",", ORGANIZER_EMAILS));
    s.put("ORGANIZER_HTTPS_ONLY", "true");
    s.put("RATE_LIMIT_REGISTRATIONS_PER_MINUTE", "100000");
    s.put("RATE_LIMIT_EXPORTS_PER_MINUTE", "100000");
    s.put("MAX_REQUEST_BYTES", "16384");
    s.put("RETENTION_DAYS", "365");
    return s;
  }

  public static Db db() {
    ensureInfrastructure();
    return new Db(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  public static Mailpit mailpit() {
    ensureInfrastructure();
    return new Mailpit("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025));
  }

  public static RecaptchaMock recaptcha() {
    ensureInfrastructure();
    return recaptcha;
  }

  public static Path jsonCopyDir() {
    ensureInfrastructure();
    return jsonCopyDir;
  }

  /** Writes an options file in a fresh directory and returns its path. */
  public static Path writeOptionsFile(String json) {
    try {
      Path dir = Files.createTempDirectory(workDir, "options");
      return Files.writeString(
          dir.resolve("conference-options.json"), json, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static Path newTempDir(String prefix) {
    try {
      return Files.createTempDirectory(workDir, prefix);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** A local TCP port with nothing listening on it. */
  public static int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void ensureInfrastructure() {
    if (postgres != null) {
      return;
    }
    try {
      postgres = new PostgreSQLContainer("postgres:16.15-alpine");
      postgres.start();
      mailpit =
          new GenericContainer<>("axllent/mailpit:v1.31.1")
              .withExposedPorts(1025, 8025)
              .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));
      mailpit.start();
      recaptcha = RecaptchaMock.start();
      workDir = Files.createTempDirectory("acceptance");
      optionsFile = writeOptionsFile(OPTIONS_JSON);
      jsonCopyDir = Files.createDirectories(workDir.resolve("json-copies"));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
