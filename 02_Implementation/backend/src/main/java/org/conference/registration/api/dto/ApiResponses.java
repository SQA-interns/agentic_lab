package org.conference.registration.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.conference.registration.domain.ConferenceOption;
import org.conference.registration.domain.OptionCategory;
import org.conference.registration.domain.RegistrationType;
import org.conference.registration.service.RegistrationResult;

/** Response bodies of the public REST API (specification §4). */
public final class ApiResponses {

  private ApiResponses() {}

  /** An option offered on a registration form. */
  public record OptionResponse(String id, OptionCategory category, String name) {
    public static OptionResponse of(ConferenceOption option) {
      return new OptionResponse(option.id(), option.category(), option.name());
    }
  }

  /** A freshly issued anti-automation form token. */
  public record FormTokenResponse(String token) {}

  /** Returned with 201 after a registration was stored. */
  public record RegistrationResponse(UUID id, RegistrationType type, Instant createdAt) {
    public static RegistrationResponse of(RegistrationResult result) {
      return new RegistrationResponse(result.id(), result.type(), result.createdAt());
    }
  }

  /** Uniform error body. */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record ErrorResponse(
      int status, String error, String message, Map<String, String> fieldErrors) {
    public ErrorResponse {
      fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public ErrorResponse(int status, String error, String message) {
      this(status, error, message, Map.of());
    }
  }
}
