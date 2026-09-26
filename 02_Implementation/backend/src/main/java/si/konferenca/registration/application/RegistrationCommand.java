package si.konferenca.registration.application;

import java.util.List;
import si.konferenca.registration.domain.RegistrationType;

/** Raw registration input as submitted by the participant, before normalisation. */
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
    List<String> consentIds,
    String recaptchaToken) {}
