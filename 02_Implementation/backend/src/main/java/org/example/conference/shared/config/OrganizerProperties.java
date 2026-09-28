package org.example.conference.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Organizer credential from environment (AR-06); startup fails when absent. */
@Validated
@ConfigurationProperties(prefix = "app.organizer")
public record OrganizerProperties(
    @NotBlank @Size(max = 64) String username,
    @NotBlank @Size(min = 12, max = 128) String password) {

  @Override
  public String toString() {
    return "OrganizerProperties[username=" + username + ", password=<redacted>]";
  }
}
