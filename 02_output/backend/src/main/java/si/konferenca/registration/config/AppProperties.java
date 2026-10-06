package si.konferenca.registration.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of docs/02_specification.md §9, bound from the environment variables mapped in
 * application.properties.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    String environment,
    String conferenceName,
    String conferenceConfigFile,
    String jsonCopyDir,
    String mailFrom,
    boolean smtpTls,
    String allowedOrigins,
    Recaptcha recaptcha,
    Organizer organizer,
    Limits limits) {

  /** Anti-automation settings (SR-01, SR-02). */
  public record Recaptcha(boolean testMode, String siteKey, String secretKey, String verifyUrl) {}

  /** Organizer access and notification settings (BR-08, SR-06, US-007). */
  public record Organizer(String username, String password, String emails, boolean httpsOnly) {

    public List<String> emailList() {
      return split(emails);
    }
  }

  /** Rate and size limits (SR-03). */
  public record Limits(
      int registrationsPerMinute,
      int exportsPerMinute,
      int formConfigPerMinute,
      int maxRequestBytes) {}

  public List<String> allowedOriginList() {
    return split(allowedOrigins);
  }

  public boolean isProduction() {
    return "production".equals(environment);
  }

  static List<String> split(String value) {
    if (value == null) {
      return List.of();
    }
    return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
  }
}
