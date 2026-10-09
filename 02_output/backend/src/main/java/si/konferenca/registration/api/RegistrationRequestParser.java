package si.konferenca.registration.api;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import si.konferenca.registration.domain.ErrorCode;
import si.konferenca.registration.domain.Field;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.Submission;
import si.konferenca.registration.domain.ValidationError;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Turns the request body into a {@link Submission}; structural problems are MALFORMED. */
final class RegistrationRequestParser {

  static final int MAX_OPTION_IDS = 50;
  static final int MAX_CONSENT_IDS = 20;
  static final int MAX_TOKEN = 4096;
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Set<String> OTHER_KEYS =
      Set.of("type", "optionIds", "consentIds", "recaptchaToken");

  /** A parsed request, or the structural errors that prevented parsing. */
  record Parsed(Submission submission, String captchaToken, List<ValidationError> errors) {}

  private RegistrationRequestParser() {}

  static Parsed parse(String body) {
    JsonNode root;
    try {
      root = JSON.readTree(body);
    } catch (JacksonException e) {
      return malformed("type");
    }
    if (root == null || !root.isObject()) {
      return malformed("type");
    }
    for (String key : root.propertyNames()) {
      if (!OTHER_KEYS.contains(key) && Field.fromApiName(key).isEmpty()) {
        return malformed("type");
      }
    }
    JsonNode typeNode = root.get("type");
    Optional<RegistrationType> type =
        typeNode != null && typeNode.isString()
            ? RegistrationType.fromName(typeNode.asString())
            : Optional.empty();
    if (type.isEmpty()) {
      return malformed("type");
    }
    List<ValidationError> errors = new ArrayList<>();
    Map<Field, String> values = new EnumMap<>(Field.class);
    for (Field field : Field.values()) {
      JsonNode node = root.get(field.apiName());
      if (node == null || node.isNull()) {
        if (field.belongsTo(type.get())) {
          values.put(field, "");
        }
      } else if (node.isString()) {
        values.put(field, node.asString());
      } else {
        errors.add(ValidationError.of(field.apiName(), ErrorCode.MALFORMED));
      }
    }
    List<String> optionIds = ids(root.get("optionIds"), MAX_OPTION_IDS, "optionIds", errors);
    List<String> consentIds = ids(root.get("consentIds"), MAX_CONSENT_IDS, "consentIds", errors);
    JsonNode tokenNode = root.get("recaptchaToken");
    String token =
        tokenNode != null && tokenNode.isString() && tokenNode.asString().length() <= MAX_TOKEN
            ? tokenNode.asString()
            : null;
    if (!errors.isEmpty()) {
      return new Parsed(null, null, errors);
    }
    return new Parsed(new Submission(type.get(), values, optionIds, consentIds), token, List.of());
  }

  private static List<String> ids(
      JsonNode node, int max, String field, List<ValidationError> errors) {
    List<String> ids = new ArrayList<>();
    if (node == null || node.isNull()) {
      return ids;
    }
    if (!node.isArray() || node.size() > max) {
      errors.add(ValidationError.of(field, ErrorCode.MALFORMED));
      return ids;
    }
    for (JsonNode item : node) {
      if (!item.isString()) {
        errors.add(ValidationError.of(field, ErrorCode.MALFORMED));
        return ids;
      }
      ids.add(item.asString());
    }
    return ids;
  }

  private static Parsed malformed(String field) {
    return new Parsed(null, null, List.of(ValidationError.of(field, ErrorCode.MALFORMED)));
  }
}
