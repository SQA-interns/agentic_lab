package si.konferenca.registration.service;

import java.util.List;

/** A submitted registration as received, before trimming and validation. */
public record RegistrationCommand(
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
    String recaptchaToken) {

  public RegistrationCommand {
    optionIds = optionIds == null ? List.of() : List.copyOf(optionIds);
    consentIds = consentIds == null ? List.of() : List.copyOf(consentIds);
  }
}
