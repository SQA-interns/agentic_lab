package si.konferenca.registration.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCatalog;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.Text;

/** Server-side validation of a registration (SB-01, BR-02..BR-05, spec section 4). */
public class RegistrationValidator {

  static final Pattern EMAIL =
      Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", Pattern.UNICODE_CHARACTER_CLASS);
  static final int MAX_NAME = 100;
  static final int MAX_EMAIL = 254;
  static final int MAX_INSTITUTION = 200;
  static final int MAX_STUDENT_ID = 50;
  static final int MAX_OPTIONS = 50;

  private final OptionCatalog catalog;

  public RegistrationValidator(OptionCatalog catalog) {
    this.catalog = catalog;
  }

  /**
   * Validates the command and returns the registration it describes.
   *
   * @throws ValidationException with every field error
   * @throws MalformedRequestException if the structure is wrong
   */
  public Registration validate(RegistrationCommand c, UUID id, Instant receivedAt) {
    List<FieldError> errors = new ArrayList<>();
    if (c.type() == null) {
      errors.add(new FieldError("type", FieldError.REQUIRED));
    }
    String firstName = text(errors, "firstName", c.firstName(), MAX_NAME, true);
    String lastName = text(errors, "lastName", c.lastName(), MAX_NAME, true);
    String email = text(errors, "email", c.email(), MAX_EMAIL, true);
    if (email != null && !EMAIL.matcher(email).matches()) {
      errors.add(new FieldError("email", FieldError.INVALID_EMAIL));
      email = null;
    }
    boolean external = c.type() == RegistrationType.EXTERNAL;
    boolean student = c.type() == RegistrationType.STUDENT;
    String organization =
        typed(errors, "organization", c.organization(), MAX_INSTITUTION, external);
    String institution =
        typed(errors, "studyInstitution", c.studyInstitution(), MAX_INSTITUTION, student);
    String programme =
        typed(errors, "studyProgramme", c.studyProgramme(), MAX_INSTITUTION, student);
    String studentId = typed(errors, "studentId", c.studentId(), MAX_STUDENT_ID, student);
    List<ConferenceOption> options = options(errors, c.optionIds(), c.type());
    if (!Boolean.TRUE.equals(c.consentGiven())) {
      errors.add(new FieldError("consentGiven", FieldError.CONSENT_REQUIRED));
    }
    if (Text.isBlank(c.recaptchaToken())) {
      errors.add(new FieldError("recaptchaToken", FieldError.RECAPTCHA_FAILED));
    }
    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
    return new Registration(
        id,
        c.type(),
        firstName,
        lastName,
        email,
        organization,
        institution,
        programme,
        studentId,
        options,
        catalog.consent(),
        receivedAt);
  }

  /** A field of one type only: required for that type, not allowed for the other. */
  private static String typed(
      List<FieldError> errors, String field, String value, int max, boolean forThisType) {
    if (forThisType) {
      return text(errors, field, value, max, true);
    }
    if (!Text.isBlank(value)) {
      errors.add(new FieldError(field, FieldError.NOT_ALLOWED_FOR_TYPE));
    }
    return null;
  }

  private static String text(
      List<FieldError> errors, String field, String value, int max, boolean required) {
    String v = Text.strip(value);
    if (v == null || v.isEmpty()) {
      if (required) {
        errors.add(new FieldError(field, FieldError.REQUIRED));
      }
      return null;
    }
    if (Text.hasForbiddenCharacters(v)) {
      errors.add(new FieldError(field, FieldError.INVALID_CHARACTERS));
      return null;
    }
    if (Text.length(v) > max) {
      errors.add(new FieldError(field, FieldError.TOO_LONG));
      return null;
    }
    return v;
  }

  private List<ConferenceOption> options(
      List<FieldError> errors, List<String> ids, RegistrationType type) {
    if (ids == null) {
      errors.add(new FieldError("optionIds", FieldError.REQUIRED));
      return List.of();
    }
    if (ids.size() > MAX_OPTIONS || new HashSet<>(ids).size() != ids.size()) {
      throw new MalformedRequestException("optionIds must be unique and at most " + MAX_OPTIONS);
    }
    List<ConferenceOption> selected = new ArrayList<>();
    for (int i = 0; i < ids.size(); i++) {
      String field = "optionIds[" + i + "]";
      Optional<ConferenceOption> option =
          ids.get(i) == null ? Optional.empty() : catalog.find(ids.get(i));
      if (option.isEmpty()) {
        errors.add(new FieldError(field, FieldError.UNKNOWN_OPTION));
      } else if (!option.get().active()) {
        errors.add(new FieldError(field, FieldError.INACTIVE_OPTION));
      } else if (type != null && !option.get().isOfferedTo(type)) {
        errors.add(new FieldError(field, FieldError.OPTION_NOT_OFFERED));
      } else {
        selected.add(option.get());
      }
    }
    return selected;
  }
}
