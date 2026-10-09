package si.konferenca.registration.application;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.OptionCatalogue;
import si.konferenca.registration.domain.ParticipantDetails;
import si.konferenca.registration.domain.RegistrationType;

/**
 * Validates a submitted registration on the trusted side (SB-01; specification section 4): text
 * fields, options against the active configured set (SR-04) and consents.
 */
public class RegistrationValidator {

  static final String OPTION_IDS = "optionIds";
  static final String CONSENT_IDS = "consentIds";

  /** A registration that passed validation. */
  public record ValidRegistration(
      RegistrationType type,
      ParticipantDetails participant,
      List<ConferenceOption> options,
      List<ConsentDefinition> consents) {
    public ValidRegistration {
      options = List.copyOf(options);
      consents = List.copyOf(consents);
    }
  }

  /** The text fields of BR-01 with their labels and maximum lengths (D-15). */
  enum TextField {
    FIRST_NAME("firstName", "First name", 100, RegistrationCommand::firstName),
    LAST_NAME("lastName", "Last name", 100, RegistrationCommand::lastName),
    EMAIL("email", "Email", 254, RegistrationCommand::email),
    ORGANIZATION(
        "organization", "Organization / institution", 200, RegistrationCommand::organization),
    STUDY_INSTITUTION(
        "studyInstitution", "Study institution", 200, RegistrationCommand::studyInstitution),
    STUDY_PROGRAMME("studyProgramme", "Study programme", 200, RegistrationCommand::studyProgramme),
    STUDENT_ID("studentId", "Student ID", 50, RegistrationCommand::studentId);

    final String name;
    final String label;
    final int maxLength;
    final Function<RegistrationCommand, String> value;

    TextField(
        String name, String label, int maxLength, Function<RegistrationCommand, String> value) {
      this.name = name;
      this.label = label;
      this.maxLength = maxLength;
      this.value = value;
    }

    static List<TextField> of(RegistrationType type) {
      return type == RegistrationType.EXTERNAL
          ? List.of(FIRST_NAME, LAST_NAME, EMAIL, ORGANIZATION)
          : List.of(FIRST_NAME, LAST_NAME, EMAIL, STUDY_INSTITUTION, STUDY_PROGRAMME, STUDENT_ID);
    }
  }

  private final OptionCatalogue catalogue;

  public RegistrationValidator(OptionCatalogue catalogue) {
    this.catalogue = catalogue;
  }

  /**
   * Returns the trimmed, checked registration.
   *
   * @throws ValidationException with every field error found
   */
  public ValidRegistration validate(RegistrationCommand command) {
    List<ValidationException.FieldError> errors = new ArrayList<>();
    Map<TextField, String> values = new EnumMap<>(TextField.class);
    List<TextField> fields = TextField.of(command.type());
    for (TextField field : TextField.values()) {
      String raw = field.value.apply(command);
      if (fields.contains(field)) {
        checkText(field, raw, errors).ifPresent(value -> values.put(field, value));
      } else if (raw != null) {
        errors.add(
            error(
                field.name,
                "not_allowed",
                field.label + " does not belong to this registration type."));
      }
    }
    List<ConferenceOption> options = checkOptions(command, errors);
    List<ConsentDefinition> consents = checkConsents(command, errors);
    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
    ParticipantDetails participant =
        new ParticipantDetails(
            values.get(TextField.FIRST_NAME),
            values.get(TextField.LAST_NAME),
            values.get(TextField.EMAIL),
            values.get(TextField.ORGANIZATION),
            values.get(TextField.STUDY_INSTITUTION),
            values.get(TextField.STUDY_PROGRAMME),
            values.get(TextField.STUDENT_ID));
    return new ValidRegistration(command.type(), participant, options, consents);
  }

  private static Optional<String> checkText(
      TextField field, String raw, List<ValidationException.FieldError> errors) {
    String value = raw == null ? "" : raw.strip();
    if (value.isEmpty()) {
      errors.add(error(field.name, "required", field.label + " is required."));
    } else if (value.codePoints().anyMatch(c -> Character.getType(c) == Character.CONTROL)) {
      errors.add(
          error(
              field.name,
              "invalid_characters",
              field.label + " must not contain line breaks or other control characters."));
    } else if (value.codePointCount(0, value.length()) > field.maxLength) {
      errors.add(
          error(
              field.name,
              "too_long",
              field.label + " must be at most " + field.maxLength + " characters."));
    } else if (field == TextField.EMAIL && !EmailAddress.isValid(value)) {
      errors.add(error(field.name, "invalid_email", "Enter a valid email address."));
    } else {
      return Optional.of(value);
    }
    return Optional.empty();
  }

  private List<ConferenceOption> checkOptions(
      RegistrationCommand command, List<ValidationException.FieldError> errors) {
    List<ConferenceOption> selected = new ArrayList<>();
    Map<Category, Integer> counts = new EnumMap<>(Category.class);
    for (String id : new LinkedHashSet<>(command.optionIds())) {
      Optional<ConferenceOption> found = catalogue.option(id);
      if (found.isEmpty()) {
        errors.add(error(OPTION_IDS, "unknown_option", "A selected option does not exist."));
        continue;
      }
      ConferenceOption option = found.get();
      if (!option.active()) {
        errors.add(error(OPTION_IDS, "inactive_option", option.name() + " is no longer offered."));
      } else if (!option.isAvailableTo(command.type())) {
        errors.add(
            error(
                OPTION_IDS,
                "option_not_available",
                option.name() + " is not available for this registration type."));
      } else {
        selected.add(option);
        counts.merge(option.category(), 1, Integer::sum);
      }
    }
    counts.forEach(
        (category, count) -> {
          int max = catalogue.maxSelections(category);
          if (count > max) {
            errors.add(
                error(
                    OPTION_IDS,
                    "too_many_options",
                    "Select at most "
                        + max
                        + " of "
                        + category.label().toLowerCase(Locale.ROOT)
                        + "."));
          }
        });
    return selected;
  }

  private List<ConsentDefinition> checkConsents(
      RegistrationCommand command, List<ValidationException.FieldError> errors) {
    Set<String> given = new LinkedHashSet<>(command.consentIds());
    List<ConsentDefinition> consents = new ArrayList<>();
    for (String id : given) {
      Optional<ConsentDefinition> consent = catalogue.consent(id);
      if (consent.isEmpty()) {
        errors.add(error(CONSENT_IDS, "unknown_consent", "A selected consent does not exist."));
      } else {
        consents.add(consent.get());
      }
    }
    boolean missingMandatory =
        catalogue.consents().stream()
            .anyMatch(consent -> consent.mandatory() && !given.contains(consent.id()));
    if (missingMandatory) {
      errors.add(error(CONSENT_IDS, "consent_required", "Please give the required consent."));
    }
    return consents;
  }

  private static ValidationException.FieldError error(String field, String code, String message) {
    return new ValidationException.FieldError(field, code, message);
  }
}
