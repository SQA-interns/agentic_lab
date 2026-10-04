package si.konferenca.registration.application;

import java.util.List;

/**
 * A submitted registration exactly as received; any value may be null. Validation is the job of
 * {@link RegistrationValidator}.
 */
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
    Boolean consentGiven,
    String captchaToken) {

  public RegistrationCommand {
    optionIds = optionIds == null ? null : java.util.Collections.unmodifiableList(optionIds);
  }
}
