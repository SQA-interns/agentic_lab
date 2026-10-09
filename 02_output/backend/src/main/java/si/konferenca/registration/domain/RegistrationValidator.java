package si.konferenca.registration.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validates a submission against the field rules and the catalogue (SB-01, BR-02 to BR-05,
 * docs/02_specification.md section 5) and builds the accepted registration.
 */
public final class RegistrationValidator {

  private static final Pattern EMAIL =
      Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", Pattern.UNICODE_CHARACTER_CLASS);

  private final ConferenceCatalogue catalogue;
  private final Clock clock;

  public RegistrationValidator(ConferenceCatalogue catalogue, Clock clock) {
    this.catalogue = catalogue;
    this.clock = clock;
  }

  /** Result of a validation: either errors or the accepted registration. */
  public record Outcome(List<ValidationError> errors, Registration registration) {
    public Outcome {
      errors = List.copyOf(errors);
    }

    public boolean valid() {
      return errors.isEmpty();
    }
  }

  public Outcome validate(Submission submission) {
    List<ValidationError> errors = new ArrayList<>();
    Map<Field, String> values = validateFields(submission, errors);
    List<ConferenceOption> options = validateOptions(submission, errors);
    Instant now = clock.instant();
    List<GivenConsent> consents = validateConsents(submission, now, errors);
    if (!errors.isEmpty()) {
      return new Outcome(List.copyOf(errors), null);
    }
    Registration registration =
        new Registration(UUID.randomUUID(), submission.type(), values, options, consents, now);
    return new Outcome(List.of(), registration);
  }

  private Map<Field, String> validateFields(Submission submission, List<ValidationError> errors) {
    Map<Field, String> values = new EnumMap<>(Field.class);
    for (Field field : Field.values()) {
      String raw = submission.values().get(field);
      if (!field.belongsTo(submission.type())) {
        if (raw != null) {
          errors.add(ValidationError.of(field.apiName(), ErrorCode.MALFORMED));
        }
        continue;
      }
      String value = Text.trim(raw);
      ErrorCode problem = fieldProblem(field, value);
      if (problem == null) {
        values.put(field, value);
      } else {
        errors.add(ValidationError.of(field.apiName(), problem));
      }
    }
    return values;
  }

  private static ErrorCode fieldProblem(Field field, String value) {
    if (value.isEmpty()) {
      return ErrorCode.REQUIRED;
    }
    if (value.length() > field.maxLength()) {
      return ErrorCode.TOO_LONG;
    }
    if (Text.hasControlCharacter(value)) {
      return field == Field.EMAIL ? ErrorCode.INVALID_EMAIL : ErrorCode.MALFORMED;
    }
    if (field == Field.EMAIL && !EMAIL.matcher(value).matches()) {
      return ErrorCode.INVALID_EMAIL;
    }
    return null;
  }

  private List<ConferenceOption> validateOptions(
      Submission submission, List<ValidationError> errors) {
    List<ConferenceOption> selected = new ArrayList<>();
    boolean unavailable = false;
    for (String id : new LinkedHashSet<>(submission.optionIds())) {
      ConferenceOption option = catalogue.option(id).orElse(null);
      if (option == null || !option.selectableBy(submission.type())) {
        unavailable = true;
      } else {
        selected.add(option);
      }
    }
    if (unavailable) {
      errors.add(ValidationError.of("optionIds", ErrorCode.OPTION_NOT_AVAILABLE));
      return selected;
    }
    for (Category category : Category.values()) {
      long count = selected.stream().filter(o -> o.category() == category).count();
      if (count > catalogue.maxSelections(category)) {
        errors.add(ValidationError.of("optionIds", ErrorCode.TOO_MANY_OPTIONS));
        break;
      }
    }
    return selected;
  }

  private List<GivenConsent> validateConsents(
      Submission submission, Instant now, List<ValidationError> errors) {
    Set<String> given = new LinkedHashSet<>(submission.consentIds());
    List<GivenConsent> result = new ArrayList<>();
    for (Consent consent : catalogue.consents()) {
      if (given.remove(consent.id())) {
        result.add(new GivenConsent(consent.id(), consent.text(), now));
      } else if (consent.mandatory()) {
        errors.add(ValidationError.missingConsent(consent.id()));
      }
    }
    if (!given.isEmpty()) {
      errors.add(ValidationError.of("consentIds", ErrorCode.UNKNOWN_CONSENT));
    }
    return result;
  }
}
