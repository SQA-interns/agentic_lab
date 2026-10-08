package si.konferenca.registration.service;

import java.util.List;
import java.util.Objects;

/** A submitted registration as received, before trimming and validation. */
public record RegistrationCommand(
    String type,
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId,
    List<String> optionIds,
    List<String> consentIds,
    String recaptchaToken) {

  /** Immutable copies; a null element becomes an empty id, which the validator reports. */
  public RegistrationCommand {
    optionIds =
        optionIds == null
            ? List.of()
            : List.copyOf(optionIds.stream().map(v -> Objects.requireNonNullElse(v, "")).toList());
    consentIds =
        consentIds == null
            ? List.of()
            : List.copyOf(consentIds.stream().map(v -> Objects.requireNonNullElse(v, "")).toList());
  }
}
