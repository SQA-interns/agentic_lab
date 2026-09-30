package si.konferenca.registration.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import si.konferenca.registration.domain.Registration;

/**
 * The accepted registration as written to the JSON copy and attached to the organizer email
 * (02_contracts/registration-copy.schema.json). Components are in schema order; fields of the other
 * registration type are omitted. Contains only registration data (SR-07).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegistrationCopy(
    int schemaVersion,
    UUID id,
    Instant submittedAt,
    String type,
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId,
    List<Option> options,
    List<Consent> consents) {

  /** A selected option as shown at registration time. */
  public record Option(String id, String name, String category) {}

  /** A consent with the time it was given. */
  public record Consent(String id, Instant givenAt) {}

  public static RegistrationCopy of(Registration r) {
    return new RegistrationCopy(
        1,
        r.getId(),
        r.getSubmittedAt(),
        r.getType().name(),
        r.getFirstName(),
        r.getLastName(),
        r.getEmail(),
        r.getOrganization(),
        r.getStudyInstitution(),
        r.getStudyProgramme(),
        r.getStudentId(),
        r.getOptions().stream()
            .map(o -> new Option(o.getOptionId(), o.getOptionName(), o.getCategory()))
            .toList(),
        r.getConsents().stream().map(c -> new Consent(c.getConsentId(), c.getGivenAt())).toList());
  }
}
