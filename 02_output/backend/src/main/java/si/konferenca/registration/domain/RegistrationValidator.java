package si.konferenca.registration.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import si.konferenca.registration.domain.Registration.SelectedOption;

/** The validation rules of a registration (BR-02 to BR-05, SR-04), without side effects. */
public final class RegistrationValidator {

  public static final int MAX_OPTIONS = 50;
  public static final int MAX_CAPTCHA_TOKEN_LENGTH = 4096;

  // One address: a local part, one @, and a domain of at least two labels; no whitespace.
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$");
  private static final int LINE_SEPARATOR = 0x2028;
  private static final int PARAGRAPH_SEPARATOR = 0x2029;

  private final OptionsCatalogue catalogue;

  public RegistrationValidator(OptionsCatalogue catalogue) {
    this.catalogue = catalogue;
  }

  /** The cleaned values of a registration that passed every rule. */
  public record Validated(Map<TextField, String> values, List<SelectedOption> options) {
    public Validated {
      values = Map.copyOf(values);
      options = List.copyOf(options);
    }
  }

  /**
   * Checks every rule and reports all field errors together.
   *
   * @throws ValidationFailedException when at least one rule is broken
   */
  public Validated validate(RegistrationInput input) {
    List<FieldError> errors = new ArrayList<>(input.shapeErrors());
    Map<TextField, String> values = new EnumMap<>(TextField.class);
    for (TextField field : input.type().fields()) {
      if (!input.malformedFields().contains(field.apiName())) {
        validateText(field, input.texts().get(field), values, errors);
      }
    }
    List<SelectedOption> options = new ArrayList<>();
    if (!input.malformedFields().contains(FieldError.FIELD_OPTION_IDS)) {
      validateOptions(input.optionIds(), options, errors);
    }
    if (!input.malformedFields().contains(FieldError.FIELD_CONSENT)
        && !Boolean.TRUE.equals(input.consent())) {
      errors.add(new FieldError(FieldError.FIELD_CONSENT, FieldError.CONSENT_REQUIRED));
    }
    if (!input.malformedFields().contains(FieldError.FIELD_CAPTCHA_TOKEN)) {
      validateCaptchaToken(input.captchaToken(), errors);
    }
    if (!errors.isEmpty()) {
      throw new ValidationFailedException(errors);
    }
    return new Validated(values, options);
  }

  private static void validateText(
      TextField field, String raw, Map<TextField, String> values, List<FieldError> errors) {
    String value = raw == null ? "" : raw.strip();
    if (value.isEmpty()) {
      errors.add(new FieldError(field.apiName(), FieldError.REQUIRED));
    } else if (value.codePoints().anyMatch(RegistrationValidator::isForbiddenCharacter)) {
      errors.add(new FieldError(field.apiName(), FieldError.INVALID_CHARACTERS));
    } else if (value.codePointCount(0, value.length()) > field.maxLength()) {
      errors.add(new FieldError(field.apiName(), FieldError.TOO_LONG));
    } else if (field == TextField.EMAIL && !EMAIL.matcher(value).matches()) {
      errors.add(new FieldError(field.apiName(), FieldError.INVALID_FORMAT));
    } else {
      values.put(field, value);
    }
  }

  private static boolean isForbiddenCharacter(int codePoint) {
    return Character.isISOControl(codePoint)
        || codePoint == LINE_SEPARATOR
        || codePoint == PARAGRAPH_SEPARATOR;
  }

  private void validateOptions(
      List<String> optionIds, List<SelectedOption> options, List<FieldError> errors) {
    if (optionIds == null) {
      errors.add(new FieldError(FieldError.FIELD_OPTION_IDS, FieldError.REQUIRED));
      return;
    }
    if (optionIds.size() > MAX_OPTIONS) {
      errors.add(new FieldError(FieldError.FIELD_OPTION_IDS, FieldError.TOO_MANY));
      return;
    }
    Set<String> seen = new HashSet<>();
    boolean duplicate = false;
    boolean notSelectable = false;
    for (String id : optionIds) {
      if (!seen.add(id)) {
        duplicate = true;
        continue;
      }
      Optional<ConferenceOption> option = catalogue.findActive(id);
      if (option.isPresent()) {
        options.add(
            new SelectedOption(option.get().id(), option.get().name(), option.get().category()));
      } else {
        notSelectable = true;
      }
    }
    if (duplicate) {
      errors.add(new FieldError(FieldError.FIELD_OPTION_IDS, FieldError.OPTION_DUPLICATE));
    }
    if (notSelectable) {
      errors.add(new FieldError(FieldError.FIELD_OPTION_IDS, FieldError.OPTION_NOT_SELECTABLE));
    }
  }

  private static void validateCaptchaToken(String token, List<FieldError> errors) {
    if (token == null || token.isBlank()) {
      errors.add(new FieldError(FieldError.FIELD_CAPTCHA_TOKEN, FieldError.CAPTCHA_FAILED));
    } else if (token.length() > MAX_CAPTCHA_TOKEN_LENGTH) {
      errors.add(new FieldError(FieldError.FIELD_CAPTCHA_TOKEN, FieldError.TOO_LONG));
    }
  }
}
