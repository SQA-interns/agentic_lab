package org.conference.registration.api.dto;

import static org.conference.registration.api.dto.ValidationRules.EMAIL;
import static org.conference.registration.api.dto.ValidationRules.EMAIL_MAX;
import static org.conference.registration.api.dto.ValidationRules.HONEYPOT_MAX;
import static org.conference.registration.api.dto.ValidationRules.MSG_CONSENT;
import static org.conference.registration.api.dto.ValidationRules.MSG_EMAIL;
import static org.conference.registration.api.dto.ValidationRules.MSG_NAME;
import static org.conference.registration.api.dto.ValidationRules.MSG_OPTION;
import static org.conference.registration.api.dto.ValidationRules.MSG_REQUIRED;
import static org.conference.registration.api.dto.ValidationRules.MSG_TEXT;
import static org.conference.registration.api.dto.ValidationRules.MSG_TOO_LONG;
import static org.conference.registration.api.dto.ValidationRules.NAME;
import static org.conference.registration.api.dto.ValidationRules.NAME_MAX;
import static org.conference.registration.api.dto.ValidationRules.OPTIONS_MAX;
import static org.conference.registration.api.dto.ValidationRules.OPTION_ID;
import static org.conference.registration.api.dto.ValidationRules.TEXT;
import static org.conference.registration.api.dto.ValidationRules.TEXT_MAX;
import static org.conference.registration.api.dto.ValidationRules.TOKEN_MAX;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.conference.registration.domain.ParticipantDetails;
import org.conference.registration.domain.RegistrationType;
import org.conference.registration.service.RegistrationCommand;

/** External participant registration (US-001, FORM_SCHEMA §External participant registration). */
public record ExternalRegistrationRequest(
    @NotBlank(message = MSG_REQUIRED)
        @Size(max = NAME_MAX, message = MSG_TOO_LONG)
        @Pattern(regexp = NAME, message = MSG_NAME)
        String firstName,
    @NotBlank(message = MSG_REQUIRED)
        @Size(max = NAME_MAX, message = MSG_TOO_LONG)
        @Pattern(regexp = NAME, message = MSG_NAME)
        String lastName,
    @NotBlank(message = MSG_REQUIRED)
        @Size(max = EMAIL_MAX, message = MSG_TOO_LONG)
        @Email(message = MSG_EMAIL)
        @Pattern(regexp = EMAIL, message = MSG_EMAIL)
        String email,
    @NotBlank(message = MSG_REQUIRED)
        @Size(max = TEXT_MAX, message = MSG_TOO_LONG)
        @Pattern(regexp = TEXT, message = MSG_TEXT)
        String organization,
    @NotNull(message = MSG_CONSENT) @AssertTrue(message = MSG_CONSENT) Boolean privacyConsent,
    @Size(max = OPTIONS_MAX, message = MSG_OPTION)
        List<
                @NotNull(message = MSG_OPTION) @Pattern(regexp = OPTION_ID, message = MSG_OPTION)
                String>
            optionIds,
    @NotBlank(message = MSG_REQUIRED) @Size(max = TOKEN_MAX, message = MSG_TOO_LONG)
        String formToken,
    @Size(max = HONEYPOT_MAX) String website) {

  public ExternalRegistrationRequest {
    optionIds = optionIds == null ? List.of() : List.copyOf(optionIds);
  }

  public RegistrationCommand toCommand() {
    return new RegistrationCommand(
        RegistrationType.EXTERNAL,
        ParticipantDetails.external(firstName, lastName, email, organization),
        Boolean.TRUE.equals(privacyConsent),
        optionIds,
        formToken,
        website);
  }
}
