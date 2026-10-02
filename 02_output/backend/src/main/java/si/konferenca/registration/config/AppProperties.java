package si.konferenca.registration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The settings of the specification (section 5), bound from application.properties. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    String environment,
    String mailFrom,
    String conferenceName,
    String organizerEmails,
    String optionsFile,
    String consentText,
    String jsonCopyDir,
    Recaptcha recaptcha,
    Organizer organizer,
    String corsAllowedOrigin,
    RateLimit rateLimit,
    int maxRequestBytes,
    boolean trustForwardedHeaders) {

  /** Anti-automation settings (SR-01, SR-02). */
  public record Recaptcha(boolean testMode, String siteKey, String secretKey, String verifyUrl) {}

  /** The single organizer account (BR-08, SR-06). */
  public record Organizer(String username, String password, boolean requireHttps) {}

  /** Requests per minute and client for each group of operations (SR-03). */
  public record RateLimit(int registration, int export, int read) {}

  public boolean production() {
    return "production".equals(environment);
  }
}
