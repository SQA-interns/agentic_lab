package si.konferenca.registration.application;

import java.util.List;
import si.konferenca.registration.domain.RegistrationType;

/**
 * A submitted registration as received; text fields are untrimmed and null when absent. Validation
 * turns it into a registration or field errors.
 */
public record RegistrationCommand(
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
    String captchaToken) {

  public RegistrationCommand {
    optionIds = optionIds == null ? List.of() : List.copyOf(optionIds);
    consentIds = consentIds == null ? List.of() : List.copyOf(consentIds);
  }
}
