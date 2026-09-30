package si.konferenca.registration.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.FieldError;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationSubmission;

/** Request and response bodies of docs/02_contracts/openapi.yaml. */
final class Dtos {

  private Dtos() {}

  /** RegistrationRequest; unknown properties are rejected. */
  @JsonIgnoreProperties(ignoreUnknown = false)
  record RegistrationRequest(
      String type,
      String firstName,
      String lastName,
      String email,
      String organization,
      String studyInstitution,
      String studyProgramme,
      String studentId,
      List<String> optionIds,
      List<String> consentIds,
      String captchaToken) {

    RegistrationSubmission toSubmission() {
      return new RegistrationSubmission(
          type,
          firstName,
          lastName,
          email,
          organization,
          studyInstitution,
          studyProgramme,
          studentId,
          optionIds,
          consentIds,
          captchaToken);
    }
  }

  record OptionDto(String id, String name, String category) {

    static OptionDto of(ConferenceOption o) {
      return new OptionDto(o.id(), o.name(), o.category().name());
    }
  }

  record ConsentDto(String id, String text, boolean mandatory) {

    static ConsentDto of(ConsentDefinition c) {
      return new ConsentDto(c.id(), c.text(), c.mandatory());
    }
  }

  record OptionsResponse(
      String type,
      List<OptionDto> options,
      List<ConsentDto> consents,
      Map<String, Integer> categoryLimits) {}

  record ClientConfig(String conferenceName, boolean captchaTestMode, String recaptchaSiteKey) {}

  record RegistrationConfirmation(
      UUID reference,
      String type,
      String firstName,
      String lastName,
      String email,
      List<OptionDto> options,
      Instant submittedAt) {

    static RegistrationConfirmation of(Registration r) {
      return new RegistrationConfirmation(
          r.reference(),
          r.type().name(),
          r.firstName(),
          r.lastName(),
          r.email(),
          r.options().stream()
              .map(o -> new OptionDto(o.optionId(), o.optionName(), o.category().name()))
              .toList(),
          r.submittedAt());
    }
  }

  record FieldErrorDto(String field, String message) {

    static FieldErrorDto of(FieldError e) {
      return new FieldErrorDto(e.field(), e.message());
    }
  }

  record ErrorResponse(String message, List<FieldErrorDto> errors) {

    static ErrorResponse of(String message) {
      return new ErrorResponse(message, List.of());
    }
  }
}
