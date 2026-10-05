package si.konferenca.registration.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.RegistrationType;

/** Server-side validation rules of specification section 4 (SB-01, BR-02 to BR-05). */
public final class RegistrationValidator {

  static final Pattern EMAIL =
      Pattern.compile(
          "^[^\\s@<>()\\[\\],;:\"\\\\]+@(?:[^\\s@<>()\\[\\],;:\"\\\\.]+\\.)+"
              + "[^\\s@<>()\\[\\],;:\"\\\\.]{2,}$");
  static final int NAME_MAX = 100;
  static final int LONG_TEXT_MAX = 200;
  static final int STUDENT_ID_MAX = 50;
  static final int EMAIL_MAX = 254;
  static final int OPTIONS_MAX = 100;

  /** A registration that passed every field rule. */
  public record ValidRegistration(
      RegistrationType type,
      Participant participant,
      List<ConferenceOptions.Option> options,
      String captchaToken) {

    public ValidRegistration {
      options = List.copyOf(options);
    }
  }

  /**
   * Validates every field and collects all errors.
   *
   * @throws RegistrationRejectedException with every field error found
   */
  public ValidRegistration validate(RegistrationCommand command, ConferenceOptions configured) {
    Set<FieldError> errors = new LinkedHashSet<>();
    RegistrationType type = type(command.type(), errors);
    String firstName = text(command.firstName(), "firstName", NAME_MAX, errors);
    String lastName = text(command.lastName(), "lastName", NAME_MAX, errors);
    String email = email(command.email(), errors);
    String organization = null;
    String studyInstitution = null;
    String studyProgramme = null;
    String studentId = null;
    if (type == RegistrationType.EXTERNAL) {
      organization = text(command.organization(), "organization", LONG_TEXT_MAX, errors);
      notAllowed(command.studyInstitution(), "studyInstitution", errors);
      notAllowed(command.studyProgramme(), "studyProgramme", errors);
      notAllowed(command.studentId(), "studentId", errors);
    } else if (type == RegistrationType.STUDENT) {
      notAllowed(command.organization(), "organization", errors);
      studyInstitution =
          text(command.studyInstitution(), "studyInstitution", LONG_TEXT_MAX, errors);
      studyProgramme = text(command.studyProgramme(), "studyProgramme", LONG_TEXT_MAX, errors);
      studentId = text(command.studentId(), "studentId", STUDENT_ID_MAX, errors);
    }
    List<ConferenceOptions.Option> options = options(command.optionIds(), configured, errors);
    if (!Boolean.TRUE.equals(command.consentGiven())) {
      errors.add(new FieldError("consentGiven", "CONSENT_REQUIRED"));
    }
    String token = command.captchaToken();
    if (token == null || token.isBlank()) {
      errors.add(new FieldError("captchaToken", "REQUIRED"));
    }
    if (!errors.isEmpty()) {
      throw new RegistrationRejectedException(List.copyOf(errors));
    }
    Participant participant =
        new Participant(
            firstName, lastName, email, organization, studyInstitution, studyProgramme, studentId);
    return new ValidRegistration(type, participant, options, token.strip());
  }

  private static RegistrationType type(String value, Set<FieldError> errors) {
    if (value == null || value.isBlank()) {
      errors.add(new FieldError("type", "REQUIRED"));
      return null;
    }
    for (RegistrationType type : RegistrationType.values()) {
      if (type.name().equals(value)) {
        return type;
      }
    }
    errors.add(new FieldError("type", "NOT_ALLOWED"));
    return null;
  }

  private static String text(String value, String field, int max, Set<FieldError> errors) {
    String stripped = value == null ? "" : value.strip();
    if (stripped.isEmpty()) {
      errors.add(new FieldError(field, "REQUIRED"));
      return null;
    }
    if (hasControlCharacter(stripped)) {
      errors.add(new FieldError(field, "INVALID_CHARACTERS"));
      return null;
    }
    if (stripped.codePointCount(0, stripped.length()) > max) {
      errors.add(new FieldError(field, "TOO_LONG"));
      return null;
    }
    return stripped;
  }

  private static String email(String value, Set<FieldError> errors) {
    String stripped = text(value, "email", EMAIL_MAX, errors);
    if (stripped != null && !EMAIL.matcher(stripped).matches()) {
      errors.add(new FieldError("email", "INVALID_EMAIL"));
      return null;
    }
    return stripped;
  }

  private static void notAllowed(String value, String field, Set<FieldError> errors) {
    if (value != null) {
      errors.add(new FieldError(field, "NOT_ALLOWED"));
    }
  }

  private static List<ConferenceOptions.Option> options(
      List<String> ids, ConferenceOptions configured, Set<FieldError> errors) {
    List<ConferenceOptions.Option> selected = new ArrayList<>();
    if (ids == null) {
      errors.add(new FieldError("optionIds", "REQUIRED"));
      return selected;
    }
    if (ids.size() > OPTIONS_MAX) {
      errors.add(new FieldError("optionIds", "TOO_LONG"));
      return selected;
    }
    Set<String> seen = new HashSet<>();
    for (String id : ids) {
      ConferenceOptions.Option option = id == null ? null : configured.find(id).orElse(null);
      if (option == null) {
        errors.add(new FieldError("optionIds", "UNKNOWN_OPTION"));
      } else if (!option.active()) {
        errors.add(new FieldError("optionIds", "INACTIVE_OPTION"));
      } else if (!seen.add(id)) {
        errors.add(new FieldError("optionIds", "DUPLICATE_OPTION"));
      } else {
        selected.add(option);
      }
    }
    return selected;
  }

  static boolean hasControlCharacter(String value) {
    return value
        .codePoints()
        .anyMatch(c -> Character.getType(c) == Character.CONTROL || c == 0x2028 || c == 0x2029);
  }
}
