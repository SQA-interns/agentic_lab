package si.konferenca.registration.domain;

/**
 * The fixed participant fields of BR-01. External participants have an organization; students have
 * the three study fields; the fields of the other type are null.
 */
public record Participant(
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId) {}
