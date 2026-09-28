package org.example.conference.registration.api;

import static org.example.conference.shared.text.InputPatterns.EMAIL;
import static org.example.conference.shared.text.InputPatterns.MAX_EMAIL;
import static org.example.conference.shared.text.InputPatterns.MAX_LONG_TEXT;
import static org.example.conference.shared.text.InputPatterns.MAX_NAME;
import static org.example.conference.shared.text.InputPatterns.NO_CONTROL_CHARS;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

/** US-001 external participant form (fixed fields). Strings arrive trimmed. */
public record ExternalRegistrationRequest(
    @NotNull UUID clientRequestId,
    @NotBlank @Size(max = MAX_NAME) @Pattern(regexp = NO_CONTROL_CHARS) String firstName,
    @NotBlank @Size(max = MAX_NAME) @Pattern(regexp = NO_CONTROL_CHARS) String lastName,
    @NotBlank @Size(max = MAX_EMAIL) @Pattern(regexp = EMAIL) String email,
    @NotBlank @Size(max = MAX_LONG_TEXT) @Pattern(regexp = NO_CONTROL_CHARS) String organization,
    @Valid SelectionsRequest selections,
    @Size(max = 32) Map<String, Boolean> consents,
    @NotBlank @Size(max = 4096) String captchaToken) {}
