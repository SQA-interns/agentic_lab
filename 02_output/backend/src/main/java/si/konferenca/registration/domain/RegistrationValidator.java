package si.konferenca.registration.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The validation rules of BR-01 to BR-05 (docs/02_specification.md §4), on the trusted side. */
public final class RegistrationValidator {

  static final int NAME_MAX = 100;
  static final int INSTITUTION_MAX = 200;
  static final int STUDENT_ID_MAX = 50;
  static final int CAPTCHA_TOKEN_MAX = 4096;

  private final ConferenceCatalog catalog;

  public RegistrationValidator(ConferenceCatalog catalog) {
    this.catalog = catalog;
  }

  /** A submission that passed validation: what will be stored, without id and time. */
  public record ValidRegistration(
      RegistrationType type,
      Participant participant,
      List<ConferenceOption> options,
      List<ConsentDefinition> consents) {

    public ValidRegistration {
      options = List.copyOf(options);
      consents = List.copyOf(consents);
    }
  }

  /** Either a valid registration or the field errors, never both. */
  public record Result(Optional<ValidRegistration> registration, List<FieldError> errors) {

    public Result {
      errors = List.copyOf(errors);
    }

    public boolean isValid() {
      return registration.isPresent();
    }
  }

  public Result validate(RegistrationSubmission s) {
    List<FieldError> errors = new ArrayList<>();
    Optional<RegistrationType> type = RegistrationType.fromValue(s.type());
    if (type.isEmpty()) {
      errors.add(FieldError.of("type", ErrorCode.REQUIRED));
    }
    String firstName = text(errors, "firstName", s.firstName(), NAME_MAX);
    String lastName = text(errors, "lastName", s.lastName(), NAME_MAX);
    String email = email(errors, s.email());
    String organization = null;
    String studyInstitution = null;
    String studyProgramme = null;
    String studentId = null;
    if (type.orElse(null) == RegistrationType.EXTERNAL) {
      organization = text(errors, "organization", s.organization(), INSTITUTION_MAX);
      notAllowed(errors, "studyInstitution", s.studyInstitution());
      notAllowed(errors, "studyProgramme", s.studyProgramme());
      notAllowed(errors, "studentId", s.studentId());
    } else if (type.orElse(null) == RegistrationType.STUDENT) {
      notAllowed(errors, "organization", s.organization());
      studyInstitution = text(errors, "studyInstitution", s.studyInstitution(), INSTITUTION_MAX);
      studyProgramme = text(errors, "studyProgramme", s.studyProgramme(), INSTITUTION_MAX);
      studentId = text(errors, "studentId", s.studentId(), STUDENT_ID_MAX);
    }
    List<ConferenceOption> options = options(errors, type.orElse(null), s.optionIds());
    List<ConsentDefinition> consents = consents(errors, s.consents());
    text(errors, "captchaToken", s.captchaToken(), CAPTCHA_TOKEN_MAX);
    if (!errors.isEmpty()) {
      return new Result(Optional.empty(), errors);
    }
    Participant participant =
        new Participant(
            firstName, lastName, email, organization, studyInstitution, studyProgramme, studentId);
    return new Result(
        Optional.of(new ValidRegistration(type.orElseThrow(), participant, options, consents)),
        List.of());
  }

  /** A required text: stripped, not empty, no control characters, not too long. */
  private static String text(List<FieldError> errors, String field, String value, int max) {
    String stripped = value == null ? "" : value.strip();
    if (stripped.isEmpty()) {
      errors.add(FieldError.of(field, ErrorCode.REQUIRED));
    } else if (hasControlCharacter(stripped)) {
      errors.add(FieldError.of(field, ErrorCode.INVALID_CHARACTERS));
    } else if (stripped.length() > max) {
      errors.add(FieldError.of(field, ErrorCode.TOO_LONG));
    } else {
      return stripped;
    }
    return null;
  }

  private static String email(List<FieldError> errors, String value) {
    String stripped = text(errors, "email", value, EmailAddress.MAX_LENGTH);
    if (stripped != null && !EmailAddress.isValid(stripped)) {
      errors.add(FieldError.of("email", ErrorCode.INVALID_EMAIL));
      return null;
    }
    return stripped;
  }

  private static void notAllowed(List<FieldError> errors, String field, String value) {
    if (value != null && !value.isBlank()) {
      errors.add(FieldError.of(field, ErrorCode.NOT_ALLOWED));
    }
  }

  static boolean hasControlCharacter(String value) {
    return value.codePoints().anyMatch(c -> Character.getType(c) == Character.CONTROL);
  }

  private List<ConferenceOption> options(
      List<FieldError> errors, RegistrationType type, List<String> ids) {
    Set<ErrorCode> codes = new LinkedHashSet<>();
    Set<String> seen = new HashSet<>();
    List<ConferenceOption> selected = new ArrayList<>();
    for (String id : ids) {
      if (!seen.add(id)) {
        codes.add(ErrorCode.DUPLICATE_OPTION);
        continue;
      }
      Optional<ConferenceOption> option = id == null ? Optional.empty() : catalog.option(id);
      if (option.isEmpty()) {
        codes.add(ErrorCode.UNKNOWN_OPTION);
      } else if (!option.get().active()) {
        codes.add(ErrorCode.INACTIVE_OPTION);
      } else if (type != null && !option.get().isOfferedTo(type)) {
        codes.add(ErrorCode.OPTION_NOT_OFFERED);
      } else {
        selected.add(option.get());
      }
    }
    codes.forEach(code -> errors.add(FieldError.of("optionIds", code)));
    return selected;
  }

  private List<ConsentDefinition> consents(List<FieldError> errors, List<String> ids) {
    Set<String> given = new LinkedHashSet<>(ids);
    if (given.stream().anyMatch(id -> id == null || catalog.consent(id).isEmpty())) {
      errors.add(FieldError.of("consents", ErrorCode.UNKNOWN_CONSENT));
    }
    List<ConsentDefinition> result = new ArrayList<>();
    for (ConsentDefinition consent : catalog.consents()) {
      if (given.contains(consent.id())) {
        result.add(consent);
      } else if (consent.mandatory()) {
        errors.add(FieldError.of("consents." + consent.id(), ErrorCode.CONSENT_REQUIRED));
      }
    }
    return result;
  }
}
