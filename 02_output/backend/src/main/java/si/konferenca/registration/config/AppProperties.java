package si.konferenca.registration.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application settings; every value comes from the environment (specification section 5). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    String conferenceName,
    String mailFrom,
    Smtp smtp,
    String optionsFile,
    String jsonCopyDir,
    Captcha captcha,
    Organizer organizer,
    String corsAllowedOrigin,
    RateLimit rateLimit,
    int maxRequestBytes) {

  /** Outgoing mail server. */
  public record Smtp(String host, int port, boolean starttls, String username, String password) {}

  /** Anti-automation settings (SR-01, SR-02). */
  public record Captcha(boolean testMode, String siteKey, String secretKey, String verifyUrl) {}

  /** Organizer login and notification recipients (BR-08, SR-06). */
  public record Organizer(String username, String password, String emails, boolean httpsOnly) {

    public List<String> emailList() {
      if (emails == null || emails.isBlank()) {
        return List.of();
      }
      return Arrays.stream(emails.split(",")).map(String::strip).filter(e -> !e.isEmpty()).toList();
    }
  }

  /** Requests per client per minute and endpoint group (SR-03, SB-06). */
  public record RateLimit(int registrations, int tokens, int exports, int formConfig) {}
}
