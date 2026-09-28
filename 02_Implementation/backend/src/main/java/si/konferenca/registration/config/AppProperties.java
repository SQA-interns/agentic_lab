package si.konferenca.registration.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Typed application configuration ({@code app.*}), populated from environment variables. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @DefaultValue Recaptcha recaptcha,
    @DefaultValue Mail mail,
    @DefaultValue Backup backup,
    @DefaultValue Options options,
    @DefaultValue Organizer organizer,
    @DefaultValue RateLimit rateLimit,
    @DefaultValue Request request) {

  /** reCAPTCHA settings. Test mode is off unless explicitly enabled. */
  public record Recaptcha(
      @DefaultValue("false") boolean testMode,
      String secretKey,
      String siteKey,
      @DefaultValue("https://www.google.com/recaptcha/api/siteverify") String verifyUrl) {}

  /** Email sender and organizer recipients. */
  public record Mail(
      @DefaultValue("no-reply@conference.local") String from,
      @DefaultValue List<String> organizerEmails) {}

  /** Directory for raw JSON registration backups. */
  public record Backup(@DefaultValue("./data/registrations") String dir) {}

  /** Optional path of the conference option configuration file. */
  public record Options(String file) {}

  /** Organizer credentials for the export. No password means the export is disabled. */
  public record Organizer(@DefaultValue("organizer") String username, String password) {}

  /** Per-client rate limits. */
  public record RateLimit(@DefaultValue Limit registration, @DefaultValue Limit organizer) {}

  /** A limit of {@code requests} per {@code windowSeconds}. */
  public record Limit(@DefaultValue("10") int requests, @DefaultValue("600") long windowSeconds) {}

  /** Request body size limit. */
  public record Request(@DefaultValue("16384") long maxBodyBytes) {}
}
