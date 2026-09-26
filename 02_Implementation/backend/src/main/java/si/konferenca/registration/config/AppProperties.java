package si.konferenca.registration.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import si.konferenca.registration.domain.OptionCategory;

/** Application configuration bound from {@code app.*} (environment variables / config files). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    Conference conference,
    Recaptcha recaptcha,
    Mail mail,
    Backup backup,
    Organizer organizer,
    RateLimit rateLimit,
    Request request,
    Cors cors) {

  public AppProperties {
    conference = conference == null ? new Conference(List.of(), List.of()) : conference;
    cors = cors == null ? new Cors(List.of()) : cors;
  }

  public record Conference(List<OptionEntry> options, List<ConsentEntry> consents) {
    public Conference {
      options = options == null ? List.of() : List.copyOf(options);
      consents = consents == null ? List.of() : List.copyOf(consents);
    }
  }

  public record OptionEntry(String id, String name, OptionCategory category, boolean active) {}

  public record ConsentEntry(String id, String label, boolean required) {}

  public record Recaptcha(boolean testMode, String secretKey, String siteKey, String verifyUrl) {}

  public record Mail(String from, List<String> organizerRecipients) {
    public Mail {
      organizerRecipients =
          organizerRecipients == null
              ? List.of()
              : organizerRecipients.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
  }

  public record Backup(Path directory) {}

  public record Organizer(String username, String password) {}

  public record RateLimit(int maxRequests, Duration window) {}

  public record Request(long maxBodyBytes) {}

  public record Cors(List<String> allowedOrigins) {
    public Cors {
      allowedOrigins =
          allowedOrigins == null
              ? List.of()
              : allowedOrigins.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
  }
}
