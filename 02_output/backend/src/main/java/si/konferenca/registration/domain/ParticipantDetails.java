package si.konferenca.registration.domain;

/**
 * The fixed participant fields of BR-01, trimmed; fields of the other registration type are null.
 */
public record ParticipantDetails(
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId) {}
