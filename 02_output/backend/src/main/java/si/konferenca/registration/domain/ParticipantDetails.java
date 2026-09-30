package si.konferenca.registration.domain;

/** The trimmed participant fields of an accepted registration (BR-01, BR-02). */
public record ParticipantDetails(
    RegistrationType type,
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId) {}
