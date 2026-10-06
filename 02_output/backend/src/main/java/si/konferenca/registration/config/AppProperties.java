package si.konferenca.registration.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of `docs/02_specification.md` section 4. Bound from `app.*` in application.yml, which
 * maps each environment variable; secrets have no default.
 */
@ConfigurationProperties("app")
public record AppProperties(
    String environment,
    String conferenceName,
    String conferenceConfigFile,
    String jsonCopyDir,
    Mail mail,
    Recaptcha recaptcha,
    Organizer organizer,
    String corsAllowedOrigin,
    RateLimit rateLimit,
    int maxRequestBytes) {

  /** SMTP connection and sender. */
  public record Mail(
      String host, int port, boolean starttls, String username, String password, String from) {}

  /** reCAPTCHA v2 settings (SR-01, SR-02). */
  public record Recaptcha(boolean testMode, String siteKey, String secretKey, String verifyUrl) {}

  /** Organizer access and notification recipients (BR-08, SR-06). */
  public record Organizer(String username, String password, String emails, boolean httpsOnly) {

    public List<String> emailList() {
      return emails == null
          ? List.of()
          : Arrays.stream(emails.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
  }

  /** Requests per minute per client address (SR-03). */
  public record RateLimit(int registrations, int exports, int config) {}

  public boolean isProduction() {
    return "production".equals(environment);
  }

  public boolean isLocalOrTest() {
    return "local".equals(environment) || "test".equals(environment);
  }
}
