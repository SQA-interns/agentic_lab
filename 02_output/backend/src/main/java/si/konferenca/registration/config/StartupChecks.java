package si.konferenca.registration.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail-fast checks S-1 to S-4 of specification section 3. Messages name the setting, never its
 * value (ES-07).
 */
final class StartupChecks {

  static final int MIN_ORGANIZER_PASSWORD = 16;

  private StartupChecks() {}

  static List<String> problems(AppProperties p) {
    List<String> problems = new ArrayList<>();
    AppProperties.Recaptcha captcha = p.recaptcha();
    if (p.production() && captcha.testMode()) {
      problems.add("RECAPTCHA_TEST_MODE must be false when APP_ENVIRONMENT is production");
    }
    if (!captcha.testMode() && (isBlank(captcha.siteKey()) || isBlank(captcha.secretKey()))) {
      problems.add(
          "RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY are required unless RECAPTCHA_TEST_MODE is"
              + " true");
    }
    if (p.production() && !p.organizer().httpsOnly()) {
      problems.add("ORGANIZER_HTTPS_ONLY must be true when APP_ENVIRONMENT is production");
    }
    if (isBlank(p.organizer().username())) {
      problems.add("ORGANIZER_USERNAME is required");
    }
    String password = p.organizer().password();
    if (password == null || password.length() < MIN_ORGANIZER_PASSWORD) {
      problems.add(
          "ORGANIZER_PASSWORD is required and must have at least "
              + MIN_ORGANIZER_PASSWORD
              + " characters");
    }
    if (p.organizer().emailList().isEmpty()) {
      problems.add("ORGANIZER_EMAILS is required");
    }
    if (isBlank(p.optionsFile())) {
      problems.add("CONFERENCE_OPTIONS_FILE is required");
    }
    if (p.mail().maxAttempts() < 1) {
      problems.add("MAIL_MAX_ATTEMPTS must be at least 1");
    }
    return problems;
  }

  static void verify(AppProperties p) {
    List<String> problems = problems(p);
    if (!problems.isEmpty()) {
      throw new IllegalStateException(
          "Unsafe or incomplete configuration: " + String.join("; ", problems));
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
