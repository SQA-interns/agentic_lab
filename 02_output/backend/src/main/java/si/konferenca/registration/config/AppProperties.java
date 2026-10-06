package si.konferenca.registration.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Environment-specific settings (spec section 5, ES-01); bound from application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    String conferenceName,
    String mailFrom,
    List<String> organizerEmails,
    String jsonCopyDir,
    String optionsFile,
    String corsAllowedOrigin,
    long maxRequestBytes,
    Smtp smtp,
    Recaptcha recaptcha,
    Organizer organizer,
    RateLimit rateLimit) {

  public AppProperties {
    organizerEmails =
        organizerEmails == null
            ? List.of()
            : List.copyOf(
                organizerEmails.stream().map(String::strip).filter(s -> !s.isEmpty()).toList());
  }

  /** SMTP server. */
  public record Smtp(String host, int port, boolean starttls, String username, String password) {}

  /** reCAPTCHA settings (SR-01, SR-02). */
  public record Recaptcha(boolean testMode, String siteKey, String secretKey, String verifyUrl) {}

  /** Organizer export login (BR-08, SR-06). */
  public record Organizer(String username, String password, boolean httpsOnly) {}

  /** Requests per minute and client address (SR-03). */
  public record RateLimit(int registrationPerMinute, int exportPerMinute, int optionsPerMinute) {}
}
