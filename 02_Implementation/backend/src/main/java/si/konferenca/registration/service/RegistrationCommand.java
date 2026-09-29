package si.konferenca.registration.service;

import java.util.List;

/** A submission as received, before normalization and validation (specification §6). */
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
    Boolean personalDataConsent,
    String recaptchaToken) {}
