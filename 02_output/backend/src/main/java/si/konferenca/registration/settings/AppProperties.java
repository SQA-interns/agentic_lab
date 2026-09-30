package si.konferenca.registration.settings;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All application settings ({@code app.*}), bound from the environment variables listed in
 * docs/02_contracts/configuration.md (ES-01). Secrets have no default; startup checks reject empty
 * required values.
 */
@ConfigurationProperties("app")
public record AppProperties(
    @DefaultValue("Conference") String conferenceName,
    @DefaultValue Mail mail,
    @DefaultValue("classpath:conference-options.json") String optionsFile,
    @DefaultValue("./data/json") String jsonCopyDir,
    @DefaultValue Recaptcha recaptcha,
    @DefaultValue Organizer organizer,
    @DefaultValue Cors cors,
    @DefaultValue RateLimit rateLimit,
    @DefaultValue("16384") long maxRequestBytes) {

  /** Sender settings. */
  public record Mail(@DefaultValue("registration@conference.local") String from) {}

  /** reCAPTCHA settings; test mode is off unless configured (SR-02). */
  public record Recaptcha(
      @DefaultValue("false") boolean testMode,
      String siteKey,
      String secretKey,
      @DefaultValue("https://www.google.com/recaptcha/api/siteverify") String verifyUrl) {

    @Override
    public String toString() {
      return "Recaptcha[testMode=" + testMode + ", verifyUrl=" + verifyUrl + "]";
    }
  }

  /** The single organizer account and its notification addresses. */
  public record Organizer(
      String username,
      String password,
      @DefaultValue List<String> emails,
      @DefaultValue("true") boolean httpsOnly) {

    @Override
    public String toString() {
      return "Organizer[username="
          + username
          + ", emails="
          + emails
          + ", httpsOnly="
          + httpsOnly
          + "]";
    }
  }

  /** Allowed cross-origin callers (local development only). */
  public record Cors(@DefaultValue List<String> allowedOrigins) {}

  /** Requests per client per minute (SR-03). */
  public record RateLimit(
      @DefaultValue("10") int registrationPerMinute,
      @DefaultValue("10") int exportPerMinute,
      @DefaultValue("120") int readPerMinute) {}
}
