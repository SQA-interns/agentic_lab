package si.konferenca.registration.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Every environment-specific setting of specification section 3 (ES-01). Secrets have no default;
 * {@link StartupChecks} rejects unsafe or incomplete combinations.
 */
@ConfigurationProperties("app")
public record AppProperties(
    @DefaultValue("production") String environment,
    @DefaultValue("Conference") String conferenceName,
    String optionsFile,
    @DefaultValue("/data/registrations") String jsonCopyDir,
    @DefaultValue("16384") long maxRequestBytes,
    @DefaultValue Mail mail,
    @DefaultValue Organizer organizer,
    @DefaultValue Recaptcha recaptcha,
    @DefaultValue Cors cors,
    @DefaultValue RateLimit rateLimit) {

  /** Mail sender and retry settings (D-11). */
  public record Mail(
      @DefaultValue("registration@konferenca.si") String from,
      @DefaultValue("true") boolean starttls,
      @DefaultValue("PT5M") Duration retryInterval,
      @DefaultValue("10") int maxAttempts) {}

  /** Organizer account and notification recipients (BR-08, SR-06). */
  public record Organizer(
      String username, String password, String emails, @DefaultValue("true") boolean httpsOnly) {

    public List<String> emailList() {
      if (emails == null) {
        return List.of();
      }
      return Arrays.stream(emails.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public String toString() {
      return "Organizer[username=" + username + ", httpsOnly=" + httpsOnly + "]";
    }
  }

  /** reCAPTCHA v2 (SR-01, SR-02). */
  public record Recaptcha(
      @DefaultValue("false") boolean testMode,
      @DefaultValue("") String siteKey,
      @DefaultValue("") String secretKey,
      @DefaultValue("https://www.google.com/recaptcha/api/siteverify") String verifyUrl) {

    @Override
    public String toString() {
      return "Recaptcha[testMode=" + testMode + ", verifyUrl=" + verifyUrl + "]";
    }
  }

  /** Allowed cross-origin callers; empty in production (local development only). */
  public record Cors(@DefaultValue("") String allowedOrigins) {

    public List<String> origins() {
      return Arrays.stream(allowedOrigins.split(","))
          .map(String::strip)
          .filter(s -> !s.isEmpty())
          .toList();
    }
  }

  /** Per-client rate limits (SR-03, SB-06). */
  public record RateLimit(
      @DefaultValue("20") int registrationPer10Min, @DefaultValue("10") int exportPerMin) {}

  public boolean production() {
    return "production".equalsIgnoreCase(environment);
  }
}
