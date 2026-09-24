package org.conference.registration.domain;

/**
 * Fixed participant fields (FORM_SCHEMA). Variant-specific fields are {@code null} for the other
 * variant.
 */
public record ParticipantDetails(
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId) {

  public static ParticipantDetails external(
      String firstName, String lastName, String email, String organization) {
    return new ParticipantDetails(firstName, lastName, email, organization, null, null, null);
  }

  public static ParticipantDetails student(
      String firstName,
      String lastName,
      String email,
      String studyInstitution,
      String studyProgramme,
      String studentId) {
    return new ParticipantDetails(
        firstName, lastName, email, null, studyInstitution, studyProgramme, studentId);
  }
}
