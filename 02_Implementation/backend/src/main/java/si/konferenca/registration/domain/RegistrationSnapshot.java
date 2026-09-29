package si.konferenca.registration.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The raw JSON representation of a registration (docs/contracts/registration-backup.schema.json):
 * written as the backup file and attached to the organizer notification.
 */
public record RegistrationSnapshot(
    int schemaVersion,
    UUID registrationId,
    Instant submittedAt,
    RegistrationType type,
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId,
    Instant personalDataConsentAt,
    List<SnapshotOption> options) {

  public static final int SCHEMA_VERSION = 1;

  /** An option as selected at registration time. */
  public record SnapshotOption(String id, OptionCategory category, String name) {}

  public static RegistrationSnapshot of(Registration r) {
    return new RegistrationSnapshot(
        SCHEMA_VERSION,
        r.getId(),
        r.getSubmittedAt(),
        r.getType(),
        r.getFirstName(),
        r.getLastName(),
        r.getEmail(),
        r.getOrganization(),
        r.getStudyInstitution(),
        r.getStudyProgramme(),
        r.getStudentId(),
        r.getPersonalDataConsentAt(),
        r.getOptions().stream()
            .map(o -> new SnapshotOption(o.getId(), o.getCategory(), o.getName()))
            .toList());
  }

  public Registration.Participant participant() {
    return new Registration.Participant(
        firstName, lastName, email, organization, studyInstitution, studyProgramme, studentId);
  }
}
