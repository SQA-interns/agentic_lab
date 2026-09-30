package lab.conference.registration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lab.conference.options.Catalog;
import lab.conference.options.CatalogOption;
import lab.conference.options.ConsentFixture;
import lab.conference.options.GroupId;
import lab.conference.platform.ApiException;
import lab.conference.platform.FieldError;

/**
 * Authoritative server-side validation (SB-01, BR-01..BR-04, SR-03). Collects every field error; a
 * rejected submission never reaches storage.
 */
public class RegistrationValidator {

  public static final int MAX_OPTIONS_PER_GROUP = 50;
  static final String FIRST_NAME = "firstName";
  static final String LAST_NAME = "lastName";
  static final String EMAIL = "email";
  static final String CAPTCHA_TOKEN = "captchaToken";
  static final String CLIENT_REQUEST_ID = "clientRequestId";
  static final String CONSENT_GIVEN = "consentGiven";
  static final String INVALID_FORMAT = "INVALID_FORMAT";
  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
  static final Map<String, Integer> MAX_LENGTH =
      Map.of(
          FIRST_NAME,
          100,
          LAST_NAME,
          100,
          EMAIL,
          254,
          "organization",
          200,
          "studyInstitution",
          200,
          "studyProgramme",
          200,
          "studentId",
          64,
          CAPTCHA_TOKEN,
          4096);
  static final Map<String, String> LABELS =
      Map.of(
          FIRST_NAME,
          "First name",
          LAST_NAME,
          "Last name",
          EMAIL,
          "Email",
          "organization",
          "Organization / institution",
          "studyInstitution",
          "Study institution",
          "studyProgramme",
          "Study programme",
          "studentId",
          "Student ID",
          CAPTCHA_TOKEN,
          "Captcha");
  private static final List<String> COMMON_TEXT = List.of(FIRST_NAME, LAST_NAME, EMAIL);
  private static final Set<String> COMMON_FIELDS =
      Set.of(
          CLIENT_REQUEST_ID,
          CAPTCHA_TOKEN,
          FIRST_NAME,
          LAST_NAME,
          EMAIL,
          "selections",
          CONSENT_GIVEN);

  private final Catalog catalog;

  public RegistrationValidator(Catalog catalog) {
    this.catalog = catalog;
  }

  public ValidatedRegistration validate(FormType form, JsonNode body) {
    List<FieldError> errors = new ArrayList<>();
    if (body == null || !body.isObject()) {
      throw ApiException.validation(
          List.of(
              new FieldError(
                  "body", "MALFORMED_REQUEST", "The request body must be a JSON object.")));
    }
    rejectUnknownFields(form, body, errors);
    UUID clientRequestId = clientRequestId(body.get(CLIENT_REQUEST_ID), errors);
    Map<String, String> fields = new LinkedHashMap<>();
    List<String> textFields = new ArrayList<>(COMMON_TEXT);
    textFields.addAll(form.specificFields());
    for (String name : textFields) {
      text(name, body.get(name), errors).ifPresent(v -> fields.put(name, v));
    }
    String email = fields.get(EMAIL);
    if (email != null && !TextRules.isEmail(email)) {
      fields.remove(EMAIL);
      errors.add(new FieldError(EMAIL, "INVALID_EMAIL", "Email: enter a valid email address."));
    }
    String captcha = text(CAPTCHA_TOKEN, body.get(CAPTCHA_TOKEN), errors).orElse(null);
    Map<GroupId, List<CatalogOption>> selections = selections(body.get("selections"), errors);
    ValidatedRegistration.ConsentState consent = consent(body.get(CONSENT_GIVEN), errors);
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    return new ValidatedRegistration(clientRequestId, form, fields, selections, consent, captcha);
  }

  private static void rejectUnknownFields(FormType form, JsonNode body, List<FieldError> errors) {
    for (Iterator<String> it = body.fieldNames(); it.hasNext(); ) {
      String name = it.next();
      if (!COMMON_FIELDS.contains(name) && !form.specificFields().contains(name)) {
        errors.add(new FieldError(name, "UNKNOWN_FIELD", "This field is not part of the form."));
      }
    }
  }

  private static UUID clientRequestId(JsonNode node, List<FieldError> errors) {
    if (node == null || node.isNull()) {
      errors.add(new FieldError(CLIENT_REQUEST_ID, "REQUIRED", "A client request ID is required."));
      return null;
    }
    if (node.isTextual() && UUID_PATTERN.matcher(node.asText()).matches()) {
      return UUID.fromString(node.asText());
    }
    errors.add(
        new FieldError(CLIENT_REQUEST_ID, INVALID_FORMAT, "The client request ID must be a UUID."));
    return null;
  }

  private static Optional<String> text(String name, JsonNode node, List<FieldError> errors) {
    String label = LABELS.get(name);
    if (node != null && !node.isNull() && !node.isTextual()) {
      errors.add(new FieldError(name, INVALID_FORMAT, label + " must be text."));
      return Optional.empty();
    }
    String value = node == null || node.isNull() ? "" : TextRules.trim(node.asText());
    if (value.isEmpty()) {
      errors.add(new FieldError(name, "REQUIRED", label + " is required."));
      return Optional.empty();
    }
    if (TextRules.length(value) > MAX_LENGTH.get(name)) {
      errors.add(
          new FieldError(
              name,
              "TOO_LONG",
              label + " must be at most " + MAX_LENGTH.get(name) + " characters."));
      return Optional.empty();
    }
    if (TextRules.hasControlCharacters(value)) {
      errors.add(
          new FieldError(
              name,
              "INVALID_CHARACTERS",
              label + " must not contain line breaks or control characters."));
      return Optional.empty();
    }
    return Optional.of(value);
  }

  private Map<GroupId, List<CatalogOption>> selections(JsonNode node, List<FieldError> errors) {
    Map<GroupId, List<CatalogOption>> result = new EnumMap<>(GroupId.class);
    for (GroupId g : GroupId.values()) {
      result.put(g, new ArrayList<>());
    }
    if (node == null || node.isNull()) {
      return result;
    }
    if (!node.isObject()) {
      errors.add(new FieldError("selections", INVALID_FORMAT, "Selections must be an object."));
      return result;
    }
    for (Map.Entry<String, JsonNode> e : node.properties()) {
      String field = "selections." + e.getKey();
      Optional<GroupId> group = GroupId.fromKey(e.getKey());
      if (group.isEmpty()) {
        errors.add(new FieldError(field, "UNKNOWN_FIELD", "Unknown activity group."));
        continue;
      }
      groupSelection(group.get(), field, e.getValue(), errors)
          .ifPresent(l -> result.put(group.get(), l));
    }
    return result;
  }

  private Optional<List<CatalogOption>> groupSelection(
      GroupId group, String field, JsonNode ids, List<FieldError> errors) {
    if (ids == null || ids.isNull()) {
      return Optional.of(List.of());
    }
    if (!ids.isArray()) {
      errors.add(new FieldError(field, INVALID_FORMAT, "Selections must be a list of option IDs."));
      return Optional.empty();
    }
    if (ids.size() > MAX_OPTIONS_PER_GROUP) {
      errors.add(new FieldError(field, "TOO_MANY_OPTIONS", "Too many options selected."));
      return Optional.empty();
    }
    Set<String> problems = new LinkedHashSet<>();
    List<CatalogOption> chosen = resolve(group, ids, problems);
    for (String code : problems) {
      errors.add(new FieldError(field, code, optionMessage(code)));
    }
    return problems.isEmpty() ? Optional.of(chosen) : Optional.empty();
  }

  private List<CatalogOption> resolve(GroupId group, JsonNode ids, Set<String> problems) {
    Set<String> seen = new HashSet<>();
    List<CatalogOption> chosen = new ArrayList<>();
    for (JsonNode id : ids) {
      if (!id.isTextual()) {
        problems.add(INVALID_FORMAT);
      } else if (!seen.add(id.asText())) {
        problems.add("DUPLICATE_OPTION");
      } else {
        catalog
            .activeOption(group, id.asText())
            .ifPresentOrElse(chosen::add, () -> problems.add("UNKNOWN_OPTION"));
      }
    }
    return chosen;
  }

  private static String optionMessage(String code) {
    return switch (code) {
      case "DUPLICATE_OPTION" -> "An option was selected more than once.";
      case "UNKNOWN_OPTION" -> "A selected option is not available.";
      default -> "Selections must be option IDs.";
    };
  }

  private ValidatedRegistration.ConsentState consent(JsonNode node, List<FieldError> errors) {
    Optional<ConsentFixture> fixture = catalog.consent();
    if (node != null && !node.isNull() && !node.isBoolean()) {
      errors.add(new FieldError(CONSENT_GIVEN, INVALID_FORMAT, "Consent must be true or false."));
      return null;
    }
    boolean given = node != null && node.asBoolean(false);
    if (fixture.isEmpty()) {
      return null;
    }
    if (fixture.get().required() && !given) {
      errors.add(
          new FieldError(
              CONSENT_GIVEN,
              "CONSENT_REQUIRED",
              "Consent is required: please accept the consent statement."));
      return null;
    }
    return new ValidatedRegistration.ConsentState(fixture.get().id(), given);
  }
}
