package si.konferenca.registration.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.RegistrationSnapshot;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.service.RegistrationCommand;

/** Request and response bodies of docs/contracts/openapi.yaml. */
final class ApiDtos {

  private ApiDtos() {}

  /** Contract schema {@code Option}. */
  record OptionDto(String id, OptionCategory category, String name) {
    static OptionDto of(ConferenceOption o) {
      return new OptionDto(o.getId(), o.getCategory(), o.getName());
    }

    static OptionDto of(RegistrationSnapshot.SnapshotOption o) {
      return new OptionDto(o.id(), o.category(), o.name());
    }
  }

  /** Contract schema {@code ClientConfig}. */
  record ClientConfigDto(String recaptchaSiteKey, boolean recaptchaTestMode) {}

  /** Contract schema {@code RegistrationRequest}; strings stay raw until service validation. */
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
      Boolean personalDataConsent,
      String recaptchaToken) {

    RegistrationCommand toCommand() {
      return new RegistrationCommand(
          type,
          firstName,
          lastName,
          email,
          organization,
          studyInstitution,
          studyProgramme,
          studentId,
          optionIds,
          personalDataConsent,
          recaptchaToken);
    }
  }

  /** Contract schema {@code RegistrationResponse}. */
  record RegistrationResponse(
      UUID registrationId,
      Instant submittedAt,
      RegistrationType type,
      String firstName,
      String lastName,
      String email,
      String organization,
      String studyInstitution,
      String studyProgramme,
      String studentId,
      List<OptionDto> options) {

    static RegistrationResponse of(RegistrationSnapshot s) {
      return new RegistrationResponse(
          s.registrationId(),
          s.submittedAt(),
          s.type(),
          s.firstName(),
          s.lastName(),
          s.email(),
          s.organization(),
          s.studyInstitution(),
          s.studyProgramme(),
          s.studentId(),
          s.options().stream().map(OptionDto::of).toList());
    }
  }

  /** Contract schema {@code RestoreResult}. */
  record RestoreResultDto(int restored, int alreadyPresent, int failed) {}
}
