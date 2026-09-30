package si.konferenca.registration.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import si.konferenca.registration.application.ValidationException.FieldViolation;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.EmailAddresses;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

/**
 * Server-side validation of a submission (SB-01; 02_specification.md 4.1 step 3). Text is stripped
 * of surrounding whitespace (BR-02); control characters are rejected (SR-05).
 */
@Component
public class RegistrationValidator {

  static final String REQUIRED = "This field is required.";
  static final String CONTROL = "Line breaks and control characters are not allowed.";
  static final String NOT_ALLOWED = "This field is not part of the selected registration form.";
  static final int NAME_MAX = 100;
  static final int TEXT_MAX = 200;
  static final int STUDENT_ID_MAX = 50;
  static final int TOKEN_MAX = 4000;
  static final int OPTIONS_MAX = 100;

  private final OptionCatalog catalog;

  public RegistrationValidator(OptionCatalog catalog) {
    this.catalog = catalog;
  }

  /** A submission that passed validation. */
  public record ValidRegistration(
      Registration.Details details, List<ConferenceOption> options, List<String> consentIds) {}

  public ValidRegistration validate(RegistrationCommand c) {
    List<FieldViolation> errors = new ArrayList<>();
    RegistrationType type = type(c.type(), errors);
    String firstName = text("firstName", c.firstName(), NAME_MAX, errors);
    String lastName = text("lastName", c.lastName(), NAME_MAX, errors);
    String email = email(c.email(), errors);
    String organization = null;
    String studyInstitution = null;
    String studyProgramme = null;
    String studentId = null;
    if (type == RegistrationType.EXTERNAL) {
      organization = text("organization", c.organization(), TEXT_MAX, errors);
      forbid("studyInstitution", c.studyInstitution(), errors);
      forbid("studyProgramme", c.studyProgramme(), errors);
      forbid("studentId", c.studentId(), errors);
    } else if (type == RegistrationType.STUDENT) {
      studyInstitution = text("studyInstitution", c.studyInstitution(), TEXT_MAX, errors);
      studyProgramme = text("studyProgramme", c.studyProgramme(), TEXT_MAX, errors);
      studentId = text("studentId", c.studentId(), STUDENT_ID_MAX, errors);
      forbid("organization", c.organization(), errors);
    }
    List<ConferenceOption> options = options(c.optionIds(), errors);
    List<String> consents = consents(c.consents(), errors);
    token(c.recaptchaToken(), errors);
    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
    return new ValidRegistration(
        new Registration.Details(
            type,
            firstName,
            lastName,
            email,
            organization,
            studyInstitution,
            studyProgramme,
            studentId),
        options,
        consents);
  }

  private static RegistrationType type(String value, List<FieldViolation> errors) {
    if (value == null || value.isBlank()) {
      errors.add(new FieldViolation("type", REQUIRED));
      return null;
    }
    try {
      return RegistrationType.valueOf(value.strip());
    } catch (IllegalArgumentException e) {
      errors.add(new FieldViolation("type", "Choose external participant or student."));
      return null;
    }
  }

  static String text(String field, String value, int max, List<FieldViolation> errors) {
    String v = value == null ? "" : value.strip();
    if (v.isEmpty()) {
      errors.add(new FieldViolation(field, REQUIRED));
      return null;
    }
    if (hasControlCharacter(v)) {
      errors.add(new FieldViolation(field, CONTROL));
      return null;
    }
    if (v.length() > max) {
      errors.add(new FieldViolation(field, "At most " + max + " characters are allowed."));
      return null;
    }
    return v;
  }

  private static String email(String value, List<FieldViolation> errors) {
    String v = text("email", value, EmailAddresses.MAX_LENGTH, errors);
    if (v != null && !EmailAddresses.isValid(v)) {
      errors.add(new FieldViolation("email", "Enter a valid email address."));
      return null;
    }
    return v;
  }

  private static void forbid(String field, String value, List<FieldViolation> errors) {
    if (value != null) {
      errors.add(new FieldViolation(field, NOT_ALLOWED));
    }
  }

  private List<ConferenceOption> options(List<String> ids, List<FieldViolation> errors) {
    if (ids == null) {
      errors.add(new FieldViolation("optionIds", REQUIRED));
      return List.of();
    }
    if (ids.size() > OPTIONS_MAX) {
      errors.add(new FieldViolation("optionIds", "Too many options selected."));
      return List.of();
    }
    List<ConferenceOption> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String id : ids) {
      Optional<ConferenceOption> option = id == null ? Optional.empty() : catalog.find(id);
      if (option.isEmpty() || !option.get().active()) {
        errors.add(new FieldViolation("optionIds", "An option is not available: choose again."));
        return List.of();
      }
      if (!seen.add(id)) {
        errors.add(new FieldViolation("optionIds", "Each option can be selected only once."));
        return List.of();
      }
      result.add(option.get());
    }
    return result;
  }

  private List<String> consents(List<String> given, List<FieldViolation> errors) {
    if (given == null) {
      errors.add(new FieldViolation("consents", "This consent is required."));
      return List.of();
    }
    Set<String> known = new HashSet<>();
    catalog.consents().forEach(d -> known.add(d.id()));
    Set<String> seen = new HashSet<>();
    for (String id : given) {
      if (id == null || !known.contains(id) || !seen.add(id)) {
        errors.add(new FieldViolation("consents", "Unknown or repeated consent."));
        return List.of();
      }
    }
    for (ConsentDefinition d : catalog.consents()) {
      if (d.required() && !seen.contains(d.id())) {
        errors.add(new FieldViolation("consents", "This consent is required."));
        return List.of();
      }
    }
    return catalog.consents().stream().map(ConsentDefinition::id).filter(seen::contains).toList();
  }

  private static void token(String token, List<FieldViolation> errors) {
    if (token == null || token.isBlank()) {
      errors.add(new FieldViolation("recaptchaToken", "Confirm that you are not a robot."));
    } else if (token.length() > TOKEN_MAX || hasControlCharacter(token)) {
      errors.add(new FieldViolation("recaptchaToken", "The anti-robot check failed."));
    }
  }

  static boolean hasControlCharacter(String value) {
    return value
        .codePoints()
        .anyMatch(
            cp -> {
              int t = Character.getType(cp);
              return t == Character.CONTROL
                  || t == Character.LINE_SEPARATOR
                  || t == Character.PARAGRAPH_SEPARATOR;
            });
  }
}
