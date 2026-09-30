package lab.conference.platform;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings bound from the documented environment names (docs/02_specification.md
 * section 10) via application.yml.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    String profile,
    String catalogPath,
    String jsonDir,
    Captcha captcha,
    Mail mail,
    Organizer organizer,
    List<String> allowedOrigins,
    RateLimit rateLimit,
    Duration notifyPollInterval,
    Duration reconcileInterval,
    Duration reconcileGrace) {

  /** Captcha mode and reCAPTCHA keys. */
  public record Captcha(String mode, String siteKey, String secretKey) {}

  /** Outgoing mail settings. */
  public record Mail(
      String host,
      int port,
      boolean tlsEnabled,
      String username,
      String password,
      String from,
      List<String> organizerEmails) {}

  /** Organizer credential for the export. */
  public record Organizer(String username, String passwordHash) {}

  /** Requests per minute per client address. */
  public record RateLimit(int registrationPerMinute, int exportPerMinute) {}
}
