package si.konferenca.registration.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Consent;
import si.konferenca.registration.domain.EmailAddress;
import si.konferenca.registration.domain.FieldError;
import si.konferenca.registration.domain.OptionCatalogue;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.Text;

/** Server-side validation of a registration (SB-01, specification section 4). */
final class RegistrationValidator {

  static final int NAME_MAX = 100;
  static final int INSTITUTION_MAX = 200;
  static final int STUDENT_ID_MAX = 50;

  private RegistrationValidator() {}

  /** Trimmed values and the field errors found; the values are usable only without errors. */
  record Result(
      RegistrationType type,
      Participant participant,
      List<ConferenceOption> options,
      List<Consent> consents,
      List<FieldError> errors) {

    Result {
      options = List.copyOf(options);
      consents = List.copyOf(consents);
      errors = List.copyOf(errors);
    }
  }

  static Result validate(RegistrationCommand c, OptionCatalogue catalogue) {
    List<FieldError> errors = new ArrayList<>();
    RegistrationType type = type(c.type(), errors);
    String firstName = text("firstName", c.firstName(), NAME_MAX, true, errors);
    String lastName = text("lastName", c.lastName(), NAME_MAX, true, errors);
    String email = text("email", c.email(), EmailAddress.MAX_LENGTH, true, errors);
    if (email != null && !EmailAddress.isValid(email)) {
      errors.add(new FieldError("email", "invalid_email"));
    }
    boolean external = type == RegistrationType.EXTERNAL;
    boolean student = type == RegistrationType.STUDENT;
    String organization =
        typed("organization", c.organization(), INSTITUTION_MAX, external, student, errors);
    String institution =
        typed("studyInstitution", c.studyInstitution(), INSTITUTION_MAX, student, external, errors);
    String programme =
        typed("studyProgramme", c.studyProgramme(), INSTITUTION_MAX, student, external, errors);
    String studentId = typed("studentId", c.studentId(), STUDENT_ID_MAX, student, external, errors);
    List<ConferenceOption> options = options(c.optionIds(), type, catalogue, errors);
    List<Consent> consents = consents(c.consentIds(), catalogue, errors);
    Participant participant =
        new Participant(
            firstName, lastName, email, organization, institution, programme, studentId);
    return new Result(type, participant, options, consents, errors);
  }

  private static RegistrationType type(String value, List<FieldError> errors) {
    if (value == null) {
      errors.add(new FieldError("type", "required"));
      return null;
    }
    try {
      return RegistrationType.valueOf(value);
    } catch (IllegalArgumentException e) {
      errors.add(new FieldError("type", "malformed"));
      return null;
    }
  }

  /** A field of one registration type: required for it, not allowed for the other. */
  private static String typed(
      String field,
      String raw,
      int max,
      boolean requiredForType,
      boolean forbiddenForType,
      List<FieldError> errors) {
    if (forbiddenForType) {
      if (!Text.isBlank(raw)) {
        errors.add(new FieldError(field, "not_allowed_for_type"));
      }
      return null;
    }
    if (!requiredForType) {
      return null;
    }
    return text(field, raw, max, true, errors);
  }

  private static String text(
      String field, String raw, int max, boolean required, List<FieldError> errors) {
    String value = Text.trim(raw);
    if (value == null || value.isEmpty()) {
      if (required) {
        errors.add(new FieldError(field, "required"));
      }
      return null;
    }
    if (Text.length(value) > max) {
      errors.add(new FieldError(field, "too_long"));
      return null;
    }
    if (Text.hasControlCharacters(value)) {
      errors.add(new FieldError(field, "invalid_characters"));
      return null;
    }
    return value;
  }

  private static List<ConferenceOption> options(
      List<String> ids, RegistrationType type, OptionCatalogue catalogue, List<FieldError> errors) {
    Set<String> codes = new LinkedHashSet<>();
    List<ConferenceOption> selected = new ArrayList<>();
    Map<Category, Integer> counts = new EnumMap<>(Category.class);
    for (String id : new LinkedHashSet<>(ids)) {
      Optional<ConferenceOption> found = id == null ? Optional.empty() : catalogue.option(id);
      if (found.isEmpty()) {
        codes.add("unknown_option");
      } else if (!found.get().active()) {
        codes.add("inactive_option");
      } else if (type != null && !found.get().availableTo(type)) {
        codes.add("option_not_available");
      } else {
        selected.add(found.get());
        counts.merge(found.get().category(), 1, Integer::sum);
      }
    }
    for (Map.Entry<Category, Integer> e : counts.entrySet()) {
      if (e.getValue() > catalogue.limit(e.getKey())) {
        codes.add("too_many_options");
      }
    }
    codes.forEach(code -> errors.add(new FieldError("optionIds", code)));
    return selected;
  }

  private static List<Consent> consents(
      List<String> ids, OptionCatalogue catalogue, List<FieldError> errors) {
    Set<String> given = new LinkedHashSet<>(ids);
    List<Consent> consents = new ArrayList<>();
    for (String id : given) {
      Optional<Consent> consent = id == null ? Optional.empty() : catalogue.consent(id);
      if (consent.isEmpty()) {
        errors.add(new FieldError("consentIds", "unknown_consent"));
        break;
      }
      consents.add(consent.get());
    }
    boolean missing =
        catalogue.consents().stream().anyMatch(c -> c.mandatory() && !given.contains(c.id()));
    if (missing) {
      errors.add(new FieldError("consentIds", "consent_missing"));
    }
    return consents;
  }
}
