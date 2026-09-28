package si.konferenca.registration.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

/** Immutable representation of an accepted registration, used for the JSON backup and emails. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegistrationSnapshot(
    UUID registrationId,
    RegistrationType registrationType,
    Instant createdAt,
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId,
    Map<String, Boolean> consents,
    List<Option> options) {

  /** A selected option. */
  public record Option(String id, OptionCategory category, String name) {}

  public static RegistrationSnapshot of(Registration registration) {
    return new RegistrationSnapshot(
        registration.getId(),
        registration.getType(),
        registration.getCreatedAt(),
        registration.getFirstName(),
        registration.getLastName(),
        registration.getEmail(),
        registration.getOrganization(),
        registration.getStudyInstitution(),
        registration.getStudyProgramme(),
        registration.getStudentId(),
        Map.of(Consents.PRIVACY, registration.isPrivacyConsent()),
        registration.getOptions().stream()
            .map(o -> new Option(o.getOptionId(), o.getCategory(), o.getOptionName()))
            .toList());
  }
}
