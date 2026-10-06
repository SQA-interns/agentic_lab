package si.konferenca.registration.domain;

import java.util.List;

/** A registration as submitted, before validation; any value may be missing or wrong. */
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
    List<String> consents,
    String captchaToken) {

  // Null elements become empty ids, which validation rejects as unknown.
  public RegistrationSubmission {
    optionIds = List.copyOf(withoutNulls(optionIds));
    consents = List.copyOf(withoutNulls(consents));
  }

  private static List<String> withoutNulls(List<String> ids) {
    return ids == null ? List.of() : ids.stream().map(id -> id == null ? "" : id).toList();
  }
}
