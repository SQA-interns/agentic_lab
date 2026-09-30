package lab.conference.acceptance.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lab.conference.ConferenceApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Starts the backend through its public entry point with the documented configuration names
 * (docs/02_specification.md section 10) and exposes its HTTP base URL. Only the public interface of
 * the application is used.
 */
public final class AppInstance implements AutoCloseable {

  public static final String CAPTCHA_OK = "local-captcha-ok";

  private final Map<String, String> settings;
  private final Path jsonDir;
  private final Infra.DatabaseRef database;
  private final String organizerUser;
  private final String organizerPassword;
  private final String organizerEmail;
  private ConfigurableApplicationContext context;
  private int port;

  private AppInstance(Builder b) {
    this.jsonDir = b.jsonDir;
    this.database = b.database;
    this.organizerUser = b.organizerUser;
    this.organizerPassword = b.organizerPassword;
    this.organizerEmail = b.organizerEmail;
    Map<String, String> s = new LinkedHashMap<>();
    s.put("APP_PROFILE", "test");
    s.put("DB_URL", database.url());
    s.put("DB_USERNAME", database.username());
    s.put("DB_PASSWORD", database.password());
    s.put("SMTP_HOST", b.smtpHost);
    s.put("SMTP_PORT", String.valueOf(b.smtpPort));
    s.put("SMTP_TLS_ENABLED", "false");
    s.put("MAIL_FROM", "registrations@example.test");
    s.put("ORGANIZER_EMAILS", organizerEmail);
    s.put("CAPTCHA_MODE", "stub");
    s.put("CONFERENCE_CONFIG_PATH", b.catalog.toString());
    s.put("REGISTRATION_JSON_DIR", jsonDir.toString());
    s.put("ORGANIZER_USERNAME", organizerUser);
    s.put(
        "ORGANIZER_PASSWORD_HASH",
        "{bcrypt}" + new BCryptPasswordEncoder(10).encode(organizerPassword));
    s.put("PUBLIC_BASE_URL", "http://localhost:18080");
    s.put("ALLOWED_ORIGINS", "http://localhost:18080");
    s.put("REGISTRATION_RATE_LIMIT_PER_MINUTE", "100000");
    s.put("EXPORT_RATE_LIMIT_PER_MINUTE", "100000");
    s.put("NOTIFY_POLL_INTERVAL", "PT0.5S");
    s.put("RECONCILE_INTERVAL", "PT1S");
    s.put("RECONCILE_GRACE", "PT2S");
    s.put("server.port", "0");
    s.putAll(b.overrides);
    this.settings = s;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Starts the application; throws if it refuses to start. */
  public AppInstance start() {
    List<String> args = new ArrayList<>();
    settings.forEach((k, v) -> args.add("--" + k + "=" + v));
    context =
        new SpringApplicationBuilder(ConferenceApplication.class).run(args.toArray(String[]::new));
    port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port", "0"));
    return this;
  }

  public void stop() {
    if (context != null) {
      context.close();
      context = null;
    }
  }

  @Override
  public void close() {
    stop();
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  public Api api() {
    return new Api(baseUrl());
  }

  public Path jsonDir() {
    return jsonDir;
  }

  public Store store() {
    return new Store(database, jsonDir);
  }

  public Infra.DatabaseRef database() {
    return database;
  }

  public String organizerUser() {
    return organizerUser;
  }

  public String organizerPassword() {
    return organizerPassword;
  }

  public String organizerEmail() {
    return organizerEmail;
  }

  /** Builder with defaults that point at the shared containers and a fresh database. */
  public static final class Builder {
    private Path catalog = Catalogs.path("catalog-v1.json");
    private Path jsonDir;
    private Infra.DatabaseRef database;
    private String smtpHost;
    private int smtpPort;
    private final String organizerUser = "organizer";
    private final String organizerPassword = "Org-" + UUID.randomUUID();
    private String organizerEmail =
        "organizer-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    private final Map<String, String> overrides = new LinkedHashMap<>();

    public Builder catalog(Path path) {
      this.catalog = path;
      return this;
    }

    public Builder jsonDir(Path dir) {
      this.jsonDir = dir;
      return this;
    }

    public Builder database(Infra.DatabaseRef ref) {
      this.database = ref;
      return this;
    }

    public Builder smtp(String host, int port) {
      this.smtpHost = host;
      this.smtpPort = port;
      return this;
    }

    public Builder organizerEmail(String email) {
      this.organizerEmail = email;
      return this;
    }

    public Builder set(String name, String value) {
      overrides.put(name, value);
      return this;
    }

    public AppInstance build() {
      try {
        if (jsonDir == null) {
          jsonDir = Files.createTempDirectory("registrations-");
        }
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
      if (database == null) {
        database = Infra.newDatabase();
      }
      if (smtpHost == null) {
        smtpHost = Infra.mailpit().getHost();
        smtpPort = Infra.mailpit().getMappedPort(1025);
      }
      return new AppInstance(this);
    }
  }
}
