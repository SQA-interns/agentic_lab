package si.konferenca.registration.application;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import si.konferenca.registration.application.NormalizedRegistration.Messages;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.RegistrationType;

/** Normalises and validates registration input. This is the security boundary for input. */
@Component
public class RegistrationValidator {

  static final int MAX_OPTIONS = 50;

  /** Unicode whitespace incl. no-break space and BOM, matching JavaScript {@code trim()}. */
  private static final Pattern OUTER_WHITESPACE =
      Pattern.compile("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$");

  private static final List<String> FIELD_ORDER =
      List.of(
          "type",
          "firstName",
          "lastName",
          "email",
          "organization",
          "studyInstitution",
          "studyProgramme",
          "studentId",
          "optionIds",
          "consentIds");

  private final Validator validator;
  private final ConferenceCatalog catalog;

  public RegistrationValidator(Validator validator, ConferenceCatalog catalog) {
    this.validator = validator;
    this.catalog = catalog;
  }

  /**
   * Returns the normalised registration or throws {@link ValidationException} with all field
   * violations.
   */
  public NormalizedRegistration validate(RegistrationCommand command) {
    NormalizedRegistration normalized = normalize(command);
    Map<String, String> errors = new LinkedHashMap<>();
    collectConstraintViolations(normalized, errors);
    validateOptions(normalized.optionIds(), errors);
    validateConsents(normalized.consentIds(), errors);
    if (!errors.isEmpty()) {
      throw new ValidationException(toOrderedViolations(errors));
    }
    return normalized;
  }

  static NormalizedRegistration normalize(RegistrationCommand command) {
    return new NormalizedRegistration(
        command.type(),
        clean(command.firstName()),
        clean(command.lastName()),
        clean(command.email()),
        clean(command.organization()),
        clean(command.studyInstitution()),
        clean(command.studyProgramme()),
        clean(command.studentId()),
        distinct(command.optionIds()),
        distinct(command.consentIds()));
  }

  private static String clean(String value) {
    if (value == null) {
      return null;
    }
    String stripped = OUTER_WHITESPACE.matcher(value).replaceAll("");
    return stripped.isEmpty() ? null : stripped;
  }

  private static List<String> distinct(List<String> values) {
    if (values == null) {
      return List.of();
    }
    Set<String> unique = new LinkedHashSet<>();
    for (String value : values) {
      unique.add(value == null ? "" : value.strip());
    }
    return List.copyOf(unique);
  }

  private void collectConstraintViolations(
      NormalizedRegistration normalized, Map<String, String> errors) {
    Class<?>[] groups = groupsFor(normalized.type());
    Map<String, List<String>> byField = new LinkedHashMap<>();
    for (ConstraintViolation<NormalizedRegistration> violation :
        validator.validate(normalized, groups)) {
      byField
          .computeIfAbsent(violation.getPropertyPath().toString(), k -> new ArrayList<>())
          .add(violation.getMessage());
    }
    byField.forEach((field, messages) -> errors.put(field, pickMessage(messages)));
  }

  private static Class<?>[] groupsFor(RegistrationType type) {
    if (type == RegistrationType.EXTERNAL) {
      return new Class<?>[] {Default.class, NormalizedRegistration.External.class};
    }
    if (type == RegistrationType.STUDENT) {
      return new Class<?>[] {Default.class, NormalizedRegistration.Student.class};
    }
    return new Class<?>[] {Default.class};
  }

  private static String pickMessage(List<String> messages) {
    if (messages.contains(Messages.REQUIRED)) {
      return Messages.REQUIRED;
    }
    return messages.stream().sorted().findFirst().orElseThrow();
  }

  private void validateOptions(List<String> optionIds, Map<String, String> errors) {
    if (optionIds.size() > MAX_OPTIONS) {
      errors.put("optionIds", Messages.TOO_MANY_OPTIONS);
      return;
    }
    boolean allActive = optionIds.stream().allMatch(id -> catalog.findActiveOption(id).isPresent());
    if (!allActive) {
      errors.put("optionIds", Messages.UNKNOWN_OPTION);
    }
  }

  private void validateConsents(List<String> consentIds, Map<String, String> errors) {
    if (!consentIds.stream().allMatch(catalog::isKnownConsent)) {
      errors.put("consentIds", Messages.UNKNOWN_CONSENT);
    }
    for (ConsentDefinition consent : catalog.consents()) {
      if (consent.required() && !consentIds.contains(consent.id())) {
        errors.put("consents." + consent.id(), Messages.CONSENT_REQUIRED);
      }
    }
  }

  private static List<FieldViolation> toOrderedViolations(Map<String, String> errors) {
    return errors.entrySet().stream()
        .sorted((a, b) -> Integer.compare(fieldRank(a.getKey()), fieldRank(b.getKey())))
        .map(e -> new FieldViolation(e.getKey(), e.getValue()))
        .toList();
  }

  private static int fieldRank(String field) {
    int index = FIELD_ORDER.indexOf(field);
    return index >= 0 ? index : FIELD_ORDER.size();
  }
}
