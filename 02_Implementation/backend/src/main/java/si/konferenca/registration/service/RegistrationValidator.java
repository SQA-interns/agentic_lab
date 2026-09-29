package si.konferenca.registration.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Registration.Participant;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.service.FieldError.Code;
import si.konferenca.registration.service.RegistrationExceptions.ValidationFailedException;

/**
 * Normalizes and validates a submission (specification §6). The backend is the authoritative
 * validation boundary; the frontend only mirrors these rules for usability.
 */
public class RegistrationValidator {

  static final int NAME_MAX = 100;
  static final int EMAIL_MAX = 254;
  static final int INSTITUTION_MAX = 200;
  static final int STUDENT_ID_MAX = 50;
  static final int OPTIONS_MAX = 50;

  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  /** A submission that passed validation. */
  public record ValidRegistration(
      RegistrationType type, Participant participant, List<ConferenceOption> options) {}

  /**
   * @param catalog every known option by id (active and inactive), as of submission time
   */
  public ValidRegistration validate(
      RegistrationCommand cmd, Map<String, ConferenceOption> catalog) {
    List<FieldError> errors = new ArrayList<>();

    RegistrationType type = parseType(cmd.type(), errors);
    String firstName = text(cmd.firstName(), "firstName", NAME_MAX, true, errors);
    String lastName = text(cmd.lastName(), "lastName", NAME_MAX, true, errors);
    String email = email(cmd.email(), errors);

    String organization = null;
    String studyInstitution = null;
    String studyProgramme = null;
    String studentId = null;
    if (type == RegistrationType.EXTERNAL) {
      organization = text(cmd.organization(), "organization", INSTITUTION_MAX, true, errors);
      notAllowed(cmd.studyInstitution(), "studyInstitution", errors);
      notAllowed(cmd.studyProgramme(), "studyProgramme", errors);
      notAllowed(cmd.studentId(), "studentId", errors);
    } else if (type == RegistrationType.STUDENT) {
      notAllowed(cmd.organization(), "organization", errors);
      studyInstitution =
          text(cmd.studyInstitution(), "studyInstitution", INSTITUTION_MAX, true, errors);
      studyProgramme = text(cmd.studyProgramme(), "studyProgramme", INSTITUTION_MAX, true, errors);
      studentId = text(cmd.studentId(), "studentId", STUDENT_ID_MAX, true, errors);
    }

    if (!Boolean.TRUE.equals(cmd.personalDataConsent())) {
      errors.add(new FieldError("personalDataConsent", Code.CONSENT_REQUIRED));
    }

    List<ConferenceOption> options = options(cmd.optionIds(), catalog, errors);

    if (!errors.isEmpty()) {
      throw new ValidationFailedException(errors);
    }
    return new ValidRegistration(
        type,
        new Participant(
            firstName, lastName, email, organization, studyInstitution, studyProgramme, studentId),
        options);
  }

  /**
   * Strips leading/trailing Unicode whitespace, including no-break spaces (which {@link
   * String#strip()} keeps); blank becomes {@code null} (absent).
   */
  static String normalize(String value) {
    if (value == null) {
      return null;
    }
    int start = 0;
    int end = value.length();
    while (start < end && isSpace(value.codePointAt(start))) {
      start += Character.charCount(value.codePointAt(start));
    }
    while (end > start && isSpace(value.codePointBefore(end))) {
      end -= Character.charCount(value.codePointBefore(end));
    }
    return start == end ? null : value.substring(start, end);
  }

  private static boolean isSpace(int codePoint) {
    return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
  }

  static boolean hasDisallowedCharacters(String value) {
    return value
        .codePoints()
        .anyMatch(
            cp -> {
              int t = Character.getType(cp);
              return t == Character.CONTROL || t == Character.FORMAT;
            });
  }

  private static RegistrationType parseType(String raw, List<FieldError> errors) {
    String value = normalize(raw);
    if (value == null) {
      errors.add(new FieldError("type", Code.REQUIRED));
      return null;
    }
    for (RegistrationType t : RegistrationType.values()) {
      if (t.name().equals(value)) {
        return t;
      }
    }
    errors.add(new FieldError("type", Code.INVALID_VALUE));
    return null;
  }

  private static String text(
      String raw, String field, int max, boolean required, List<FieldError> errors) {
    String value = normalize(raw);
    if (value == null) {
      if (required) {
        errors.add(new FieldError(field, Code.REQUIRED));
      }
      return null;
    }
    if (value.codePointCount(0, value.length()) > max) {
      errors.add(new FieldError(field, Code.TOO_LONG));
      return null;
    }
    if (hasDisallowedCharacters(value)) {
      errors.add(new FieldError(field, Code.INVALID_CHARACTERS));
      return null;
    }
    return value;
  }

  private static String email(String raw, List<FieldError> errors) {
    int before = errors.size();
    String value = text(raw, "email", EMAIL_MAX, true, errors);
    if (value == null || errors.size() > before) {
      return null;
    }
    if (!isValidEmail(value)) {
      errors.add(new FieldError("email", Code.INVALID_EMAIL));
      return null;
    }
    return value;
  }

  static boolean isValidEmail(String value) {
    if (!EMAIL.matcher(value).matches()) {
      return false;
    }
    String domain = value.substring(value.indexOf('@') + 1);
    return !domain.startsWith(".") && !domain.endsWith(".") && !domain.contains("..");
  }

  private static void notAllowed(String raw, String field, List<FieldError> errors) {
    if (normalize(raw) != null) {
      errors.add(new FieldError(field, Code.FIELD_NOT_ALLOWED));
    }
  }

  private static List<ConferenceOption> options(
      List<String> ids, Map<String, ConferenceOption> catalog, List<FieldError> errors) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    if (ids.size() > OPTIONS_MAX) {
      errors.add(new FieldError("optionIds", Code.TOO_MANY_OPTIONS));
      return List.of();
    }
    Set<String> seen = new LinkedHashSet<>();
    List<ConferenceOption> selected = new ArrayList<>();
    for (int i = 0; i < ids.size(); i++) {
      String id = ids.get(i);
      if (id != null && !seen.add(id)) {
        continue;
      }
      ConferenceOption option = id == null ? null : catalog.get(id);
      String field = "optionIds[" + i + "]";
      if (option == null) {
        errors.add(new FieldError(field, Code.UNKNOWN_OPTION));
      } else if (!option.isActive()) {
        errors.add(new FieldError(field, Code.INACTIVE_OPTION));
      } else {
        selected.add(option);
      }
    }
    return selected;
  }
}
