package si.konferenca.registration.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings (specification §9). Every value comes from the environment; required values
 * without a default make startup fail.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @NotBlank String conferenceName,
    @Valid @NotNull Mail mail,
    @Valid @NotNull Organizer organizer,
    @Valid @NotNull Options options,
    @Valid @NotNull Backup backup,
    @Valid @NotNull Recaptcha recaptcha,
    @Valid @NotNull RateLimit rateLimit) {

  public record Mail(@NotBlank String from) {}

  public record Organizer(
      @NotBlank String username,
      @NotBlank @Size(min = 16, message = "must be at least 16 characters") String password,
      @NotEmpty List<@NotBlank String> emails) {}

  public record Options(@NotNull Path file) {}

  public record Backup(@NotNull Path dir) {}

  public record Recaptcha(
      boolean testMode, String siteKey, String secretKey, @NotNull URI verifyUrl) {

    /** Production mode needs both keys (HUMAN_INPUTS_MANIFEST row 1). */
    public void requireKeysUnlessTestMode() {
      if (!testMode && (isBlank(siteKey) || isBlank(secretKey))) {
        throw new IllegalStateException(
            "reCAPTCHA production mode requires RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY;"
                + " refusing to start");
      }
    }

    private static boolean isBlank(String value) {
      return value == null || value.isBlank();
    }
  }

  public record RateLimit(@Min(1) int registrationPerMinute, @Min(1) int organizerPerMinute) {}
}
