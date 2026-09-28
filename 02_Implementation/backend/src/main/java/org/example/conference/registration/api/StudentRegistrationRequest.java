package org.example.conference.registration.api;

import static org.example.conference.shared.text.InputPatterns.MAX_NAME;
import static org.example.conference.shared.text.InputPatterns.MAX_STUDENT_ID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.example.conference.shared.text.RequiredEmail;
import org.example.conference.shared.text.RequiredText;

/** US-002 student form (fixed fields). Strings arrive trimmed. */
public record StudentRegistrationRequest(
    @NotNull UUID clientRequestId,
    @RequiredText(max = MAX_NAME) String firstName,
    @RequiredText(max = MAX_NAME) String lastName,
    @RequiredEmail String email,
    @RequiredText String studyInstitution,
    @RequiredText String studyProgramme,
    @RequiredText(max = MAX_STUDENT_ID) String studentId,
    @Valid SelectionsRequest selections,
    @Size(max = 32) Map<String, Boolean> consents,
    @NotBlank @Size(max = 4096) String captchaToken) {

  public StudentRegistrationRequest {
    consents = consents == null ? Map.of() : Map.copyOf(consents);
  }
}
