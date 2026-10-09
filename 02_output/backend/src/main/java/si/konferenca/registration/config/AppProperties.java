package si.konferenca.registration.config;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings (specification section 9); bound from the environment in application.yml.
 */
@ConfigurationProperties("app")
public record AppProperties(
    String conferenceName,
    String mailFrom,
    String optionsFile,
    Path jsonCopyDir,
    Recaptcha recaptcha,
    Organizer organizer,
    Smtp smtp,
    List<String> corsAllowedOrigins,
    Limits limits) {

  public AppProperties {
    corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
  }

  /** reCAPTCHA settings; the secret never appears in {@link #toString()}. */
  public record Recaptcha(boolean testMode, String siteKey, String secretKey, URI verifyUrl) {
    @Override
    public String toString() {
      return "Recaptcha[testMode=" + testMode + ", verifyUrl=" + verifyUrl + "]";
    }
  }

  /** Organizer access; the password never appears in {@link #toString()}. */
  public record Organizer(
      String username, String password, List<String> emails, boolean httpsOnly) {
    public Organizer {
      emails =
          emails == null ? List.of() : List.copyOf(emails.stream().map(String::strip).toList());
    }

    @Override
    public String toString() {
      return "Organizer[httpsOnly=" + httpsOnly + "]";
    }
  }

  /** Outgoing mail server; the password never appears in {@link #toString()}. */
  public record Smtp(String host, int port, boolean tls, String username, String password) {
    @Override
    public String toString() {
      return "Smtp[host=" + host + ", port=" + port + ", tls=" + tls + "]";
    }
  }

  /** Rate and size limits (SR-03). */
  public record Limits(
      int registrationsPerMinute, int exportsPerMinute, int formPerMinute, int maxRequestBytes) {}
}
