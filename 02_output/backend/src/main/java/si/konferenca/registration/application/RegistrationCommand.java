package si.konferenca.registration.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import si.konferenca.registration.domain.RegistrationType;

/** A submitted registration as received, before trimming and validation. */
public record RegistrationCommand(
    RegistrationType type,
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId,
    List<String> optionIds,
    Boolean consentGiven,
    String recaptchaToken) {

  public RegistrationCommand {
    optionIds = optionIds == null ? null : Collections.unmodifiableList(new ArrayList<>(optionIds));
  }
}
