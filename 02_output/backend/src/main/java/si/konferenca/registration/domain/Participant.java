package si.konferenca.registration.domain;

/** The fixed participant fields (BR-01); fields of the other type are null. */
public record Participant(
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId) {}
