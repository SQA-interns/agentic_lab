package si.konferenca.registration.domain;

/**
 * The fixed participant fields (BR-01), stripped. External participants have an organization;
 * students have the three study fields; the other fields are null.
 */
public record Participant(
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId) {

  public String fullName() {
    return firstName + " " + lastName;
  }
}
