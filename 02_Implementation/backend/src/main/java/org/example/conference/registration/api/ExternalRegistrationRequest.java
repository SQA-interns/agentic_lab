package org.example.conference.registration.api;

import static org.example.conference.shared.text.InputPatterns.MAX_NAME;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.example.conference.shared.text.RequiredEmail;
import org.example.conference.shared.text.RequiredText;

/** US-001 external participant form (fixed fields). Strings arrive trimmed. */
public record ExternalRegistrationRequest(
    @NotNull UUID clientRequestId,
    @RequiredText(max = MAX_NAME) String firstName,
    @RequiredText(max = MAX_NAME) String lastName,
    @RequiredEmail String email,
    @RequiredText String organization,
    @Valid SelectionsRequest selections,
    @Size(max = 32) Map<String, Boolean> consents,
    @NotBlank @Size(max = 4096) String captchaToken) {

  public ExternalRegistrationRequest {
    consents = consents == null ? Map.of() : Map.copyOf(consents);
  }
}
