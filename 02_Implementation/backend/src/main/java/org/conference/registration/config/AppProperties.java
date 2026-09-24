package org.conference.registration.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Application settings bound from {@code app.*} (environment variables {@code APP_*}). */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @NotBlank String conferenceName,
    @NotNull @Valid Options options,
    @NotNull @Valid Backup backup,
    @NotNull @Valid Mail mail,
    @NotNull @Valid Admin admin,
    @NotNull @Valid Antibot antibot,
    @NotNull @Valid RateLimit rateLimit) {

  /** Location of the configurable conference options file (US-003). */
  public record Options(@NotBlank String file) {}

  /** Directory for JSON registration backups (US-005). */
  public record Backup(@NotBlank String dir) {}

  /** Sender and organizer recipients (US-006, US-007). */
  public record Mail(
      @NotBlank @Email String from, @NotEmpty List<@NotBlank @Email String> organizers) {
    public Mail {
      organizers = organizers == null ? List.of() : List.copyOf(organizers);
    }
  }

  /** Organizer account protecting the Excel export (US-008). */
  public record Admin(@NotBlank String username, @NotBlank @Size(min = 12) String password) {}

  /** Anti-automation form token settings. */
  public record Antibot(
      String formTokenSecret, @PositiveOrZero long minFillSeconds, @Positive long maxAgeSeconds) {}

  /** Per-client-IP fixed-window rate limits. */
  public record RateLimit(
      @Positive long windowSeconds,
      @Positive int registrations,
      @Positive int formTokens,
      @Positive int admin) {}
}
