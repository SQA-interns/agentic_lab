package si.konferenca.registration.application;

import java.util.List;

/** A submitted registration exactly as received, before validation. */
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
    List<String> consents,
    String recaptchaToken) {}
