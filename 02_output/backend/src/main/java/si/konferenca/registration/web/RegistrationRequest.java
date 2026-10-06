package si.konferenca.registration.web;

import java.util.List;
import si.konferenca.registration.application.RegistrationCommand;
import si.konferenca.registration.domain.RegistrationType;

/** Request body of POST /api/registrations (openapi.yaml RegistrationRequest). */
public record RegistrationRequest(
    RegistrationType type,
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId,
    List<String> optionIds,
    Boolean consentGiven,
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
        consentGiven,
        recaptchaToken);
  }
}
