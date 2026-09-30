package si.konferenca.registration.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A registration as submitted, before validation (REST contract RegistrationRequest). */
public record RegistrationSubmission(
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

  public RegistrationSubmission {
    optionIds = copy(optionIds);
    consentIds = copy(consentIds);
  }

  private static List<String> copy(List<String> values) {
    // Keeps null elements so that the validator can reject them.
    return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
  }
}
