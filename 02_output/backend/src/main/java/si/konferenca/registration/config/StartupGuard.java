package si.konferenca.registration.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Refuses unsafe configurations before the application serves anything (SR-02, SR-06, ES-01).
 * Messages name the variable, never its value.
 */
public final class StartupGuard {

  private static final Set<String> ENVIRONMENTS = Set.of("production", "local", "test");
  private static final int MIN_PASSWORD_LENGTH = 16;

  private StartupGuard() {}

  public static void check(AppProperties app) {
    List<String> problems = new ArrayList<>();
    if (!ENVIRONMENTS.contains(app.environment())) {
      problems.add("APP_ENVIRONMENT must be production, local or test");
    }
    AppProperties.Organizer organizer = app.organizer();
    if (blank(organizer.username()) || blank(organizer.password())) {
      problems.add("ORGANIZER_USERNAME and ORGANIZER_PASSWORD must be set");
    }
    if (organizer.emailList().isEmpty()) {
      problems.add("ORGANIZER_EMAILS must list at least one address");
    }
    AppProperties.Recaptcha recaptcha = app.recaptcha();
    if (recaptcha.testMode() && !app.isLocalOrTest()) {
      problems.add("RECAPTCHA_TEST_MODE may be true only when APP_ENVIRONMENT is local or test");
    }
    if (!recaptcha.testMode() && (blank(recaptcha.siteKey()) || blank(recaptcha.secretKey()))) {
      problems.add("RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY must be set when test mode is off");
    }
    if (!organizer.httpsOnly() && !app.isLocalOrTest()) {
      problems.add("ORGANIZER_HTTPS_ONLY may be false only when APP_ENVIRONMENT is local or test");
    }
    if (!blank(app.corsAllowedOrigin()) && !"local".equals(app.environment())) {
      problems.add("CORS_ALLOWED_ORIGIN may be set only when APP_ENVIRONMENT is local");
    }
    if (app.isProduction()) {
      if (blank(app.conferenceConfigFile())) {
        problems.add("CONFERENCE_CONFIG_FILE must be set in production");
      }
      if (blank(app.mail().host())) {
        problems.add("MAIL_HOST must be set in production");
      }
      if (organizer.password() != null && organizer.password().length() < MIN_PASSWORD_LENGTH) {
        problems.add("ORGANIZER_PASSWORD must have at least 16 characters in production");
      }
    }
    if (!problems.isEmpty()) {
      throw new InvalidConfigurationException(String.join("; ", problems));
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
