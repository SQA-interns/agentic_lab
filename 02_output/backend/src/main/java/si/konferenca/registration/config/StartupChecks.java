package si.konferenca.registration.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses unsafe or incomplete configuration at startup (SR-02, SR-06, ES-01). The messages name
 * settings only, never values.
 */
public final class StartupChecks {

  private static final int MIN_ORGANIZER_PASSWORD_LENGTH = 16;

  private StartupChecks() {}

  /** Returns every problem found; an empty list means the configuration may start. */
  public static List<String> problems(AppProperties p, String smtpHost) {
    List<String> problems = new ArrayList<>();
    AppProperties.Organizer organizer = p.organizer();
    if (blank(organizer.username())) {
      problems.add("ORGANIZER_USERNAME is empty");
    }
    if (organizer.password() == null
        || organizer.password().length() < MIN_ORGANIZER_PASSWORD_LENGTH) {
      problems.add("ORGANIZER_PASSWORD must have at least 16 characters");
    }
    if (organizer.emailList().isEmpty()) {
      problems.add("ORGANIZER_EMAILS is empty");
    }
    if (p.maxRequestBytes() <= 0) {
      problems.add("MAX_REQUEST_BYTES must be positive");
    }
    if (p.rateLimit().registrationsPerMinute() <= 0 || p.rateLimit().exportsPerMinute() <= 0) {
      problems.add("rate limits must be positive");
    }
    if (p.retention().days() <= 0) {
      problems.add("RETENTION_DAYS must be positive");
    }
    if (p.production()) {
      AppProperties.Recaptcha recaptcha = p.recaptcha();
      if (recaptcha.testMode()) {
        problems.add("RECAPTCHA_TEST_MODE must be off in production");
      }
      if (blank(recaptcha.siteKey()) || blank(recaptcha.secretKey())) {
        problems.add("RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY are required in production");
      }
      if (!organizer.httpsOnly()) {
        problems.add("ORGANIZER_HTTPS_ONLY must be on in production");
      }
      if (!blank(p.corsAllowedOrigin())) {
        problems.add("CORS_ALLOWED_ORIGIN must be empty in production");
      }
      if (blank(p.optionsFile())) {
        problems.add("CONFERENCE_OPTIONS_FILE is required in production");
      }
      if (blank(smtpHost)) {
        problems.add("SMTP_HOST is required in production");
      }
    }
    return problems;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
