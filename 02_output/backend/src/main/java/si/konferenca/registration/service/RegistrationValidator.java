package si.konferenca.registration.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceCatalog;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.service.RegistrationRejectedException.FieldError;

/**
 * Trusted-side validation of a registration (SB-01): fields per type (BR-01, BR-02, BR-03, D-17),
 * options (BR-04, SR-04, D-11, D-14) and consents (BR-05). All field errors are collected.
 */
public final class RegistrationValidator {

  /** A validated registration with trimmed values. */
  public record Validated(
      RegistrationType type,
      Participant participant,
      List<ConferenceOption> options,
      List<ConsentDefinition> consents) {

    public Validated {
      options = List.copyOf(options);
      consents = List.copyOf(consents);
    }
  }

  private record TextField(
      String name, int maxLength, Function<RegistrationRequest, String> value) {}

  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final int MAX_OPTIONS = 50;

  private static final List<TextField> COMMON =
      List.of(
          new TextField("firstName", 100, RegistrationRequest::firstName),
          new TextField("lastName", 100, RegistrationRequest::lastName),
          new TextField("email", 254, RegistrationRequest::email));
  private static final Map<RegistrationType, List<TextField>> TYPE_FIELDS =
      Map.of(
          RegistrationType.EXTERNAL,
          List.of(new TextField("organization", 200, RegistrationRequest::organization)),
          RegistrationType.STUDENT,
          List.of(
              new TextField("studyInstitution", 200, RegistrationRequest::studyInstitution),
              new TextField("studyProgramme", 200, RegistrationRequest::studyProgramme),
              new TextField("studentId", 50, RegistrationRequest::studentId)));

  private final ConferenceCatalog catalog;

  public RegistrationValidator(ConferenceCatalog catalog) {
    this.catalog = catalog;
  }

  public Validated validate(RegistrationRequest request) {
    Set<FieldError> errors = new LinkedHashSet<>();
    RegistrationType type = type(request.type(), errors);
    Map<String, String> values = new LinkedHashMap<>();
    List<TextField> fields = new ArrayList<>(COMMON);
    if (type != null) {
      fields.addAll(TYPE_FIELDS.get(type));
      for (Map.Entry<RegistrationType, List<TextField>> other : TYPE_FIELDS.entrySet()) {
        if (other.getKey() != type) {
          for (TextField field : other.getValue()) {
            if (field.value().apply(request) != null) {
              errors.add(new FieldError(field.name(), FieldCode.NOT_ALLOWED));
            }
          }
        }
      }
    }
    for (TextField field : fields) {
      text(field, request, errors).ifPresent(value -> values.put(field.name(), value));
    }
    String email = values.get("email");
    if (email != null && !validEmail(email)) {
      errors.add(new FieldError("email", FieldCode.INVALID_FORMAT));
    }
    List<ConferenceOption> options = options(request.optionIds(), type, errors);
    List<ConsentDefinition> consents = consents(request.consentIds(), errors);
    if (!errors.isEmpty()) {
      throw new RegistrationRejectedException(ErrorCode.VALIDATION_FAILED, List.copyOf(errors));
    }
    Participant participant =
        new Participant(
            values.get("firstName"),
            values.get("lastName"),
            email,
            values.get("organization"),
            values.get("studyInstitution"),
            values.get("studyProgramme"),
            values.get("studentId"));
    return new Validated(type, participant, options, consents);
  }

  private static RegistrationType type(String raw, Set<FieldError> errors) {
    if (raw == null) {
      errors.add(new FieldError("type", FieldCode.REQUIRED));
      return null;
    }
    for (RegistrationType type : RegistrationType.values()) {
      if (type.name().equals(raw)) {
        return type;
      }
    }
    errors.add(new FieldError("type", FieldCode.NOT_ALLOWED));
    return null;
  }

  private static Optional<String> text(
      TextField field, RegistrationRequest request, Set<FieldError> errors) {
    String raw = field.value().apply(request);
    String value = raw == null ? "" : raw.strip();
    FieldCode problem = null;
    if (value.isEmpty()) {
      problem = FieldCode.REQUIRED;
    } else if (value.codePoints().anyMatch(c -> Character.getType(c) == Character.CONTROL)) {
      problem = FieldCode.CONTROL_CHARACTER;
    } else if (value.length() > field.maxLength()) {
      problem = FieldCode.TOO_LONG;
    }
    if (problem != null) {
      errors.add(new FieldError(field.name(), problem));
      return Optional.empty();
    }
    return Optional.of(value);
  }

  static boolean validEmail(String email) {
    if (!EMAIL.matcher(email).matches()) {
      return false;
    }
    String domain = email.substring(email.indexOf('@') + 1);
    return !domain.startsWith(".") && !domain.endsWith(".") && !email.contains("..");
  }

  private List<ConferenceOption> options(
      List<String> ids, RegistrationType type, Set<FieldError> errors) {
    List<ConferenceOption> selected = new ArrayList<>();
    if (ids == null) {
      errors.add(new FieldError("optionIds", FieldCode.REQUIRED));
      return selected;
    }
    if (ids.size() > MAX_OPTIONS) {
      errors.add(new FieldError("optionIds", FieldCode.TOO_LONG));
      return selected;
    }
    Set<String> seen = new HashSet<>();
    Map<Category, Integer> counts = new EnumMap<>(Category.class);
    for (String id : ids) {
      if (!seen.add(id)) {
        errors.add(new FieldError("optionIds", FieldCode.DUPLICATE_OPTION));
        continue;
      }
      Optional<ConferenceOption> found = catalog.option(id);
      if (found.isEmpty()) {
        errors.add(new FieldError("optionIds", FieldCode.UNKNOWN_OPTION));
      } else if (!found.get().active()) {
        errors.add(new FieldError("optionIds", FieldCode.INACTIVE_OPTION));
      } else if (type != null && !found.get().availableTo(type)) {
        errors.add(new FieldError("optionIds", FieldCode.OPTION_NOT_AVAILABLE));
      } else {
        selected.add(found.get());
        counts.merge(found.get().category(), 1, Integer::sum);
      }
    }
    for (Map.Entry<Category, Integer> count : counts.entrySet()) {
      if (catalog.limit(count.getKey()).map(limit -> count.getValue() > limit).orElse(false)) {
        errors.add(new FieldError("optionIds", FieldCode.CATEGORY_LIMIT));
      }
    }
    return selected;
  }

  private List<ConsentDefinition> consents(List<String> ids, Set<FieldError> errors) {
    Set<String> given = ids == null ? Set.of() : new HashSet<>(ids);
    Set<String> known = new HashSet<>();
    for (ConsentDefinition consent : catalog.consents()) {
      known.add(consent.id());
      if (!given.contains(consent.id())) {
        errors.add(new FieldError("consentIds", FieldCode.CONSENT_MISSING));
      }
    }
    if (!known.containsAll(given)) {
      errors.add(new FieldError("consentIds", FieldCode.NOT_ALLOWED));
    }
    return catalog.consents();
  }
}
