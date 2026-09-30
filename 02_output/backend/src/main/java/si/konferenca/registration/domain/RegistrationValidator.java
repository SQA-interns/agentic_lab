package si.konferenca.registration.domain;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates a submission on the trusted side (SB-01): BR-01 to BR-05, SR-04, SR-05. Collects every
 * field error; the captcha token is only checked for presence here, its verification is a separate
 * port.
 */
public final class RegistrationValidator {

  public static final int MAX_TEXT = 200;
  public static final int MAX_EMAIL = 254;

  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

  private final OptionsCatalog catalog;

  public RegistrationValidator(OptionsCatalog catalog) {
    this.catalog = catalog;
  }

  public ValidationResult validate(RegistrationSubmission s) {
    List<FieldError> errors = new ArrayList<>();
    RegistrationType type = RegistrationType.parse(s.type()).orElse(null);
    if (type == null) {
      errors.add(new FieldError("type", "Choose external participant or student."));
    }
    String firstName = required(errors, "firstName", s.firstName(), MAX_TEXT);
    String lastName = required(errors, "lastName", s.lastName(), MAX_TEXT);
    String email = email(errors, s.email());
    String organization = null;
    String studyInstitution = null;
    String studyProgramme = null;
    String studentId = null;
    if (type == RegistrationType.EXTERNAL) {
      organization = required(errors, "organization", s.organization(), MAX_TEXT);
      notApplicable(errors, "studyInstitution", s.studyInstitution());
      notApplicable(errors, "studyProgramme", s.studyProgramme());
      notApplicable(errors, "studentId", s.studentId());
    } else if (type == RegistrationType.STUDENT) {
      studyInstitution = required(errors, "studyInstitution", s.studyInstitution(), MAX_TEXT);
      studyProgramme = required(errors, "studyProgramme", s.studyProgramme(), MAX_TEXT);
      studentId = required(errors, "studentId", s.studentId(), MAX_TEXT);
      notApplicable(errors, "organization", s.organization());
    }
    List<ConferenceOption> options =
        type == null ? List.of() : options(errors, type, s.optionIds());
    List<ConsentDefinition> consents = consents(errors, s.consentIds());
    if (isBlank(s.captchaToken())) {
      errors.add(new FieldError("captchaToken", "Please confirm that you are not a robot."));
    }
    if (!errors.isEmpty()) {
      return new ValidationResult(errors, null, List.of(), List.of());
    }
    ParticipantDetails participant =
        new ParticipantDetails(
            type,
            firstName,
            lastName,
            email,
            organization,
            studyInstitution,
            studyProgramme,
            studentId);
    return new ValidationResult(List.of(), participant, options, consents);
  }

  private static String required(List<FieldError> errors, String field, String raw, int max) {
    String value = raw == null ? "" : raw.strip();
    if (value.isEmpty()) {
      errors.add(new FieldError(field, "This field is required."));
      return null;
    }
    if (value.length() > max) {
      errors.add(new FieldError(field, "Use at most " + max + " characters."));
      return null;
    }
    if (value.chars().anyMatch(Character::isISOControl)) {
      errors.add(new FieldError(field, "Line breaks and control characters are not allowed."));
      return null;
    }
    return value;
  }

  private static String email(List<FieldError> errors, String raw) {
    String value = required(errors, "email", raw, MAX_EMAIL);
    if (value == null) {
      return null;
    }
    if (!EMAIL.matcher(value).matches() || !parsesStrictly(value)) {
      errors.add(new FieldError("email", "Enter a valid email address."));
      return null;
    }
    return value;
  }

  private static boolean parsesStrictly(String value) {
    try {
      InternetAddress address = new InternetAddress(value, true);
      address.validate();
      return value.equals(address.getAddress());
    } catch (AddressException e) {
      return false;
    }
  }

  private static void notApplicable(List<FieldError> errors, String field, String raw) {
    if (!isBlank(raw)) {
      errors.add(
          new FieldError(field, "This field does not apply to the chosen registration type."));
    }
  }

  private List<ConferenceOption> options(
      List<FieldError> errors, RegistrationType type, List<String> ids) {
    List<ConferenceOption> selected = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Map<Category, Integer> counts = new EnumMap<>(Category.class);
    String problem = null;
    for (String id : ids) {
      if (id == null || !seen.add(id)) {
        problem = "Each option can be selected only once.";
        continue;
      }
      Optional<ConferenceOption> option = catalog.option(id);
      if (option.isEmpty() || !option.get().offeredTo(type)) {
        problem = "One or more selected options are not available.";
        continue;
      }
      selected.add(option.get());
      counts.merge(option.get().category(), 1, Integer::sum);
    }
    for (Map.Entry<Category, Integer> count : counts.entrySet()) {
      Optional<Integer> limit = catalog.limit(count.getKey());
      if (limit.isPresent() && count.getValue() > limit.get()) {
        problem = "Too many options selected in one category.";
      }
    }
    if (problem != null) {
      errors.add(new FieldError("optionIds", problem));
    }
    return selected;
  }

  private List<ConsentDefinition> consents(List<FieldError> errors, List<String> ids) {
    List<ConsentDefinition> given = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    boolean invalid = false;
    for (String id : ids) {
      Optional<ConsentDefinition> consent = id == null ? Optional.empty() : catalog.consent(id);
      if (consent.isEmpty() || !seen.add(id)) {
        invalid = true;
      } else {
        given.add(consent.get());
      }
    }
    boolean mandatoryMissing =
        catalog.consents().stream().anyMatch(c -> c.mandatory() && !seen.contains(c.id()));
    if (invalid) {
      errors.add(new FieldError("consentIds", "One or more consents are not recognised."));
    } else if (mandatoryMissing) {
      errors.add(new FieldError("consentIds", "Please give the required consent."));
    }
    return given;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
