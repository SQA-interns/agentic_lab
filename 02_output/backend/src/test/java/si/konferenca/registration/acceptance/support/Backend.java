package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import si.konferenca.registration.RegistrationApplication;

/**
 * Runs the backend through its entry point with the settings of docs/02_specification.md section 3,
 * passed by their environment-variable names. Tests talk to it over HTTP only.
 */
public final class Backend implements AutoCloseable {

  public static final String ORGANIZER_USERNAME = "organizer";
  public static final String ORGANIZER_PASSWORD =
      "acceptance-organizer-pass-0001"; // gitleaks:allow (test-only value)
  public static final String ORGANIZER_EMAIL_1 = "organizer1@konferenca.test";
  public static final String ORGANIZER_EMAIL_2 = "organizer2@konferenca.test";
  public static final String CONFERENCE_NAME = "Konferenca 2026";

  private final Map<String, String> settings;
  private final ConfigurableApplicationContext context;
  private final String baseUrl;

  private Backend(Map<String, String> settings) {
    this.settings = Map.copyOf(settings);
    Map<String, Object> properties = new HashMap<>(settings);
    properties.put("server.port", "0");
    properties.put("spring.main.banner-mode", "off");
    this.context =
        new SpringApplicationBuilder(RegistrationApplication.class).properties(properties).run();
    this.baseUrl = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
  }

  /** Default test settings: fresh database, empty JSON copy directory, test conference options. */
  public static Builder builder() {
    return new Builder();
  }

  public static Backend startDefault() {
    return builder().start();
  }

  public String url(String path) {
    return baseUrl + path;
  }

  public Path jsonCopyDir() {
    return Path.of(settings.get("JSON_COPY_DIR"));
  }

  /** Stops this instance and starts a new one with the same settings (same database and files). */
  public Backend restart() {
    close();
    return new Backend(settings);
  }

  @Override
  public void close() {
    if (context.isActive()) {
      context.close();
    }
  }

  /** Builder over the setting names of the specification. */
  public static final class Builder {
    private final Map<String, String> settings = new LinkedHashMap<>();

    private Builder() {
      AcceptanceStack.start();
      settings.put("APP_ENVIRONMENT", "test");
      settings.put("DATABASE_URL", AcceptanceStack.newDatabase());
      settings.put("DATABASE_USER", AcceptanceStack.databaseUser());
      settings.put("POSTGRES_PASSWORD", AcceptanceStack.databasePassword());
      settings.put("SMTP_HOST", AcceptanceStack.smtpHost());
      settings.put("SMTP_PORT", String.valueOf(AcceptanceStack.smtpPort()));
      settings.put("SMTP_TLS", "false");
      settings.put("SMTP_USERNAME", "");
      settings.put("SMTP_PASSWORD", "");
      settings.put("MAIL_FROM", "registration@konferenca.test");
      settings.put("CONFERENCE_NAME", CONFERENCE_NAME);
      settings.put("ORGANIZER_EMAILS", ORGANIZER_EMAIL_1 + "," + ORGANIZER_EMAIL_2);
      settings.put("ORGANIZER_USERNAME", ORGANIZER_USERNAME);
      settings.put("ORGANIZER_PASSWORD", ORGANIZER_PASSWORD);
      settings.put("RECAPTCHA_TEST_MODE", "true");
      settings.put("RECAPTCHA_SITE_KEY", "");
      settings.put("RECAPTCHA_SECRET_KEY", "");
      settings.put("RATE_LIMIT_REGISTRATIONS_PER_MINUTE", "10000");
      settings.put("RATE_LIMIT_EXPORTS_PER_MINUTE", "10000");
      settings.put("RATE_LIMIT_FORMS_PER_MINUTE", "10000");
      conferenceConfigResource("/acceptance/conference.json");
      try {
        settings.put("JSON_COPY_DIR", Files.createTempDirectory("json-copies").toString());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    public Builder setting(String name, String value) {
      settings.put(name, value);
      return this;
    }

    /** Uses a classpath resource as the conference options file. */
    public Builder conferenceConfigResource(String resource) {
      try (InputStream in = Backend.class.getResourceAsStream(resource)) {
        if (in == null) {
          throw new IllegalArgumentException("missing resource " + resource);
        }
        Path file = Files.createTempFile("conference", ".json");
        Files.write(file, in.readAllBytes());
        settings.put("CONFERENCE_CONFIG_PATH", file.toString());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return this;
    }

    public Backend start() {
      return new Backend(settings);
    }
  }
}
