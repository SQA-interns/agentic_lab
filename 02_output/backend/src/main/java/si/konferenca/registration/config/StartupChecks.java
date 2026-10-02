package si.konferenca.registration.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings that must hold before the backend may start (specification section 5). A violation names
 * the setting only, never its value.
 */
final class StartupChecks {

  private StartupChecks() {}

  /** The names of the violated rules; empty when the settings are acceptable. */
  static List<String> violations(AppProperties properties) {
    List<String> violations = new ArrayList<>();
    if (isBlank(properties.organizer().username()) || isBlank(properties.organizer().password())) {
      violations.add("ORGANIZER_USERNAME and ORGANIZER_PASSWORD must be set");
    }
    if (ApplicationConfig.organizerRecipients(properties).isEmpty()) {
      violations.add("ORGANIZER_EMAILS must name at least one recipient");
    }
    if (properties.maxRequestBytes() <= 0
        || properties.rateLimit().registration() <= 0
        || properties.rateLimit().export() <= 0
        || properties.rateLimit().read() <= 0) {
      violations.add("rate limits and MAX_REQUEST_BYTES must be positive");
    }
    if (properties.production()) {
      // SR-02, SR-06: production never runs with the test mode, without keys or without HTTPS.
      if (properties.recaptcha().testMode()) {
        violations.add("RECAPTCHA_TEST_MODE must be off in production");
      }
      if (isBlank(properties.recaptcha().siteKey())
          || isBlank(properties.recaptcha().secretKey())) {
        violations.add("RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY must be set in production");
      }
      if (!properties.organizer().requireHttps()) {
        violations.add("ORGANIZER_REQUIRE_HTTPS must be on in production");
      }
    } else if (!properties.recaptcha().testMode()
        && (isBlank(properties.recaptcha().siteKey())
            || isBlank(properties.recaptcha().secretKey()))) {
      violations.add("without RECAPTCHA_TEST_MODE the reCAPTCHA keys must be set");
    }
    return violations;
  }

  /** Stops the start-up when a rule is violated. */
  static void verify(AppProperties properties) {
    List<String> violations = violations(properties);
    if (!violations.isEmpty()) {
      throw new IllegalStateException("Invalid configuration: " + String.join("; ", violations));
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
