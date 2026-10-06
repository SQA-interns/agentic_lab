package si.konferenca.registration.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import si.konferenca.registration.domain.EmailAddress;

/**
 * Refuses to start with settings that are unsafe or incomplete (SR-02, SR-06,
 * docs/02_specification.md §9). Messages name the setting, never its value.
 */
public final class StartupGuard {

  private static final Set<String> ENVIRONMENTS = Set.of("production", "local", "test");
  private static final int MIN_ORGANIZER_PASSWORD_LENGTH = 16;

  private StartupGuard() {}

  public static void check(AppProperties p) {
    List<String> problems = new ArrayList<>();
    if (!ENVIRONMENTS.contains(p.environment())) {
      problems.add("APP_ENVIRONMENT must be production, local or test");
    }
    boolean production = p.isProduction() || !ENVIRONMENTS.contains(p.environment());
    AppProperties.Recaptcha captcha = p.recaptcha();
    if (captcha.testMode() && production) {
      problems.add("RECAPTCHA_TEST_MODE must be false in production");
    }
    if (!captcha.testMode() || production) {
      if (blank(captcha.siteKey())) {
        problems.add("RECAPTCHA_SITE_KEY is required");
      }
      if (blank(captcha.secretKey())) {
        problems.add("RECAPTCHA_SECRET_KEY is required");
      }
    }
    if (production && !p.organizer().httpsOnly()) {
      problems.add("ORGANIZER_HTTPS_ONLY must be true in production");
    }
    if (production && !p.smtpTls()) {
      problems.add("SMTP_TLS must be true in production");
    }
    if (production && !p.allowedOriginList().isEmpty()) {
      problems.add("ALLOWED_ORIGINS must be empty in production");
    }
    if (blank(p.organizer().username())) {
      problems.add("ORGANIZER_USERNAME is required");
    }
    String password = p.organizer().password();
    if (password == null || password.length() < MIN_ORGANIZER_PASSWORD_LENGTH) {
      problems.add("ORGANIZER_PASSWORD must have at least 16 characters");
    }
    List<String> emails = p.organizer().emailList();
    if (emails.isEmpty() || !emails.stream().allMatch(EmailAddress::isValid)) {
      problems.add("ORGANIZER_EMAILS must list valid addresses");
    }
    if (!EmailAddress.isValid(p.mailFrom())) {
      problems.add("MAIL_FROM must be a valid address");
    }
    if (blank(p.conferenceConfigFile())) {
      problems.add("CONFERENCE_CONFIG_FILE is required");
    }
    if (blank(p.jsonCopyDir())) {
      problems.add("JSON_COPY_DIR is required");
    }
    AppProperties.Limits limits = p.limits();
    if (limits.registrationsPerMinute() < 1
        || limits.exportsPerMinute() < 1
        || limits.formConfigPerMinute() < 1
        || limits.maxRequestBytes() < 1024) {
      problems.add("rate limits must be at least 1 and MAX_REQUEST_BYTES at least 1024");
    }
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Unsafe or missing settings: " + String.join("; ", problems));
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
