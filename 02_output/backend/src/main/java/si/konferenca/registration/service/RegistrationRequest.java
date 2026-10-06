package si.konferenca.registration.service;

import java.util.List;

/**
 * A submitted registration as received (`openapi.yaml` RegistrationRequest), before trimming and
 * validation. An absent property is null.
 */
public record RegistrationRequest(
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
    String antiAutomationToken) {

  public RegistrationRequest {
    optionIds = optionIds == null ? null : List.copyOf(optionIds);
    consentIds = consentIds == null ? null : List.copyOf(consentIds);
  }
}
