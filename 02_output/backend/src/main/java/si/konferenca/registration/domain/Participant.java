package si.konferenca.registration.domain;

/**
 * The fixed participant fields of BR-01; fields that do not belong to the registration type are
 * null.
 */
public record Participant(
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId) {}
