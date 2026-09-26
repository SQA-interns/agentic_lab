package si.konferenca.registration.web.dto;

import java.util.List;
import si.konferenca.registration.application.RegistrationCommand;
import si.konferenca.registration.domain.RegistrationType;

/** JSON body of {@code POST /api/registrations}. */
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
    List<String> consentIds,
    String recaptchaToken) {

  public RegistrationCommand toCommand() {
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
        consentIds,
        recaptchaToken);
  }
}
