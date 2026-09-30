package si.konferenca.registration.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Maps the documented environment variables (specification section 3, secrets.env.example) onto
 * application properties. Only variables that are actually set are mapped, so properties supplied
 * in any other way are never shadowed by empty values.
 */
public class EnvironmentAliases implements EnvironmentPostProcessor {

  static final String SOURCE_NAME = "registrationEnvironmentAliases";

  static final Map<String, String> ALIASES = aliases();

  private static Map<String, String> aliases() {
    Map<String, String> a = new LinkedHashMap<>();
    a.put("DB_URL", "spring.datasource.url");
    a.put("DB_USERNAME", "spring.datasource.username");
    a.put("POSTGRES_PASSWORD", "spring.datasource.password");
    a.put("SMTP_HOST", "spring.mail.host");
    a.put("SMTP_PORT", "spring.mail.port");
    a.put("SMTP_USERNAME", "spring.mail.username");
    a.put("SMTP_PASSWORD", "spring.mail.password");
    a.put("SMTP_AUTH", "spring.mail.properties.mail.smtp.auth");
    a.put("SMTP_STARTTLS", "app.mail.starttls");
    a.put("MAIL_FROM", "app.mail.from");
    a.put("MAIL_RETRY_INTERVAL", "app.mail.retry-interval");
    a.put("MAIL_MAX_ATTEMPTS", "app.mail.max-attempts");
    a.put("CONFERENCE_NAME", "app.conference-name");
    a.put("CONFERENCE_OPTIONS_FILE", "app.options-file");
    a.put("JSON_COPY_DIR", "app.json-copy-dir");
    a.put("MAX_REQUEST_BYTES", "app.max-request-bytes");
    a.put("ORGANIZER_USERNAME", "app.organizer.username");
    a.put("ORGANIZER_PASSWORD", "app.organizer.password");
    a.put("ORGANIZER_EMAILS", "app.organizer.emails");
    a.put("ORGANIZER_HTTPS_ONLY", "app.organizer.https-only");
    a.put("RECAPTCHA_TEST_MODE", "app.recaptcha.test-mode");
    a.put("RECAPTCHA_SITE_KEY", "app.recaptcha.site-key");
    a.put("RECAPTCHA_SECRET_KEY", "app.recaptcha.secret-key");
    a.put("RECAPTCHA_VERIFY_URL", "app.recaptcha.verify-url");
    a.put("APP_ENVIRONMENT", "app.environment");
    a.put("CORS_ALLOWED_ORIGINS", "app.cors.allowed-origins");
    a.put("RATE_LIMIT_REGISTRATION", "app.rate-limit.registration-per-10-min");
    a.put("RATE_LIMIT_EXPORT", "app.rate-limit.export-per-min");
    return Map.copyOf(a);
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> mapped = map(environment.getSystemEnvironment());
    if (!mapped.isEmpty()) {
      MapPropertySource source = new MapPropertySource(SOURCE_NAME, mapped);
      String systemEnv = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
      if (environment.getPropertySources().contains(systemEnv)) {
        environment.getPropertySources().addAfter(systemEnv, source);
      } else {
        environment.getPropertySources().addLast(source);
      }
    }
  }

  static Map<String, Object> map(Map<String, Object> variables) {
    Map<String, Object> mapped = new LinkedHashMap<>();
    ALIASES.forEach(
        (variable, property) -> {
          Object value = variables.get(variable);
          if (value != null) {
            mapped.put(property, value);
          }
        });
    return mapped;
  }
}
