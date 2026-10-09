package si.konferenca.registration.config;

import java.util.Arrays;
import org.springframework.core.env.Environment;
import si.konferenca.registration.application.EmailAddress;

/**
 * Refuses to start with an unsafe or incomplete configuration (SR-02, SB-03, ES-01). Messages name
 * the setting, never its value.
 */
final class ConfigurationGuard {

  static final String PRODUCTION_PROFILE = "production";

  private ConfigurationGuard() {}

  static void check(AppProperties properties, Environment environment) {
    boolean production =
        Arrays.asList(environment.getActiveProfiles()).contains(PRODUCTION_PROFILE);
    AppProperties.Recaptcha recaptcha = properties.recaptcha();
    if (recaptcha.testMode() && production) {
      throw new IllegalStateException(
          "RECAPTCHA_TEST_MODE must not be true with the production profile");
    }
    if (!recaptcha.testMode() && (isBlank(recaptcha.siteKey()) || isBlank(recaptcha.secretKey()))) {
      throw new IllegalStateException(
          "RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY must be set when RECAPTCHA_TEST_MODE is"
              + " false");
    }
    AppProperties.Organizer organizer = properties.organizer();
    if (isBlank(organizer.username()) || isBlank(organizer.password())) {
      throw new IllegalStateException("ORGANIZER_USERNAME and ORGANIZER_PASSWORD must be set");
    }
    if (organizer.emails().isEmpty()
        || !organizer.emails().stream().allMatch(EmailAddress::isValid)) {
      throw new IllegalStateException(
          "ORGANIZER_EMAILS must list one or more valid addresses, comma separated");
    }
    if (production && !properties.optionsFile().startsWith("file:")) {
      throw new IllegalStateException(
          "OPTIONS_FILE must be an explicit file: location with the production profile");
    }
    if (isBlank(properties.mailFrom()) || !EmailAddress.isValid(properties.mailFrom())) {
      throw new IllegalStateException("MAIL_FROM must be a valid address");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
