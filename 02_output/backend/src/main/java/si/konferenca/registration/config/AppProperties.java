package si.konferenca.registration.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** All application settings (specification section 9), bound from environment variables. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    String environment,
    String conferenceName,
    String optionsFile,
    String jsonCopyDir,
    String mailFrom,
    Recaptcha recaptcha,
    Organizer organizer,
    String corsAllowedOrigin,
    RateLimit rateLimit,
    long maxRequestBytes,
    Retention retention) {

  /** True unless the environment is explicitly local or test, so a missing setting fails safe. */
  public boolean production() {
    return !"local".equals(environment) && !"test".equals(environment);
  }

  /** reCAPTCHA settings (SR-01, SR-02). */
  public record Recaptcha(boolean testMode, String siteKey, String secretKey, String verifyUrl) {}

  /** Organizer access and notification settings (SR-06, BR-08). */
  public record Organizer(String username, String password, String emails, boolean httpsOnly) {

    /** The configured notification recipients. */
    public List<String> emailList() {
      if (emails == null) {
        return List.of();
      }
      return Arrays.stream(emails.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
  }

  /** Requests per minute and client address (SR-03). */
  public record RateLimit(int registrationsPerMinute, int exportsPerMinute) {}

  /** Retention of registrations (D-16). */
  public record Retention(int days, String cron) {}
}
