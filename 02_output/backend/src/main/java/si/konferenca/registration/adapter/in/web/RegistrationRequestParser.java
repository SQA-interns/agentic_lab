package si.konferenca.registration.adapter.in.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import si.konferenca.registration.domain.FieldError;
import si.konferenca.registration.domain.RegistrationInput;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.TextField;
import si.konferenca.registration.domain.ValidationFailedException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the body of a registration request (schema RegistrationRequest). It checks the shape of the
 * JSON only: the kind of each value and that no property is unknown; the rules are checked by the
 * domain.
 */
final class RegistrationRequestParser {

  private static final Set<String> KNOWN_FIELD_NAMES =
      Arrays.stream(TextField.values()).map(TextField::apiName).collect(Collectors.toSet());

  private final JsonMapper json =
      JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

  /**
   * Parses the body.
   *
   * @throws ValidationFailedException when the body is not a JSON object or has no valid type
   */
  RegistrationInput parse(byte[] body) {
    JsonNode root;
    try {
      root = json.readTree(body);
    } catch (JacksonException e) {
      throw malformed();
    }
    if (root == null || !root.isObject()) {
      throw malformed();
    }
    RegistrationType type = parseType(root.get(FieldError.FIELD_TYPE));

    List<FieldError> shapeErrors = new ArrayList<>();
    Set<String> malformedFields = new HashSet<>();
    Set<String> allowed = new HashSet<>();
    allowed.add(FieldError.FIELD_TYPE);

    Map<TextField, String> texts = new EnumMap<>(TextField.class);
    for (TextField field : type.fields()) {
      allowed.add(field.apiName());
      JsonNode node = root.get(field.apiName());
      if (isAbsent(node)) {
        continue;
      }
      if (node.isString()) {
        texts.put(field, node.asString());
      } else {
        invalidType(field.apiName(), shapeErrors, malformedFields);
      }
    }

    allowed.add(FieldError.FIELD_OPTION_IDS);
    List<String> optionIds = parseOptionIds(root, shapeErrors, malformedFields);

    allowed.add(FieldError.FIELD_CONSENT);
    Boolean consent = null;
    JsonNode consentNode = root.get(FieldError.FIELD_CONSENT);
    if (!isAbsent(consentNode)) {
      if (consentNode.isBoolean()) {
        consent = consentNode.asBoolean();
      } else {
        invalidType(FieldError.FIELD_CONSENT, shapeErrors, malformedFields);
      }
    }

    allowed.add(FieldError.FIELD_CAPTCHA_TOKEN);
    String captchaToken = null;
    JsonNode tokenNode = root.get(FieldError.FIELD_CAPTCHA_TOKEN);
    if (!isAbsent(tokenNode)) {
      if (tokenNode.isString()) {
        captchaToken = tokenNode.asString();
      } else {
        invalidType(FieldError.FIELD_CAPTCHA_TOKEN, shapeErrors, malformedFields);
      }
    }

    for (String name : root.propertyNames()) {
      if (!allowed.contains(name)) {
        // Only field names of the contract are echoed; any other name is reported as "body".
        String field = KNOWN_FIELD_NAMES.contains(name) ? name : FieldError.FIELD_BODY;
        shapeErrors.add(new FieldError(field, FieldError.UNKNOWN_FIELD));
      }
    }
    return new RegistrationInput(
        type, texts, optionIds, consent, captchaToken, shapeErrors, malformedFields);
  }

  private static RegistrationType parseType(JsonNode node) {
    if (isAbsent(node)) {
      throw new ValidationFailedException(
          List.of(new FieldError(FieldError.FIELD_TYPE, FieldError.REQUIRED)));
    }
    if (node.isString()) {
      for (RegistrationType type : RegistrationType.values()) {
        if (type.name().equals(node.asString())) {
          return type;
        }
      }
    }
    throw new ValidationFailedException(
        List.of(new FieldError(FieldError.FIELD_TYPE, FieldError.INVALID_TYPE)));
  }

  private static List<String> parseOptionIds(
      JsonNode root, List<FieldError> shapeErrors, Set<String> malformedFields) {
    JsonNode node = root.get(FieldError.FIELD_OPTION_IDS);
    if (isAbsent(node)) {
      return null;
    }
    if (!node.isArray()) {
      invalidType(FieldError.FIELD_OPTION_IDS, shapeErrors, malformedFields);
      return null;
    }
    List<String> optionIds = new ArrayList<>();
    for (JsonNode element : node) {
      if (!element.isString()) {
        invalidType(FieldError.FIELD_OPTION_IDS, shapeErrors, malformedFields);
        return null;
      }
      optionIds.add(element.asString());
    }
    return optionIds;
  }

  private static boolean isAbsent(JsonNode node) {
    return node == null || node.isNull();
  }

  private static void invalidType(
      String field, List<FieldError> shapeErrors, Set<String> malformedFields) {
    shapeErrors.add(new FieldError(field, FieldError.INVALID_TYPE));
    malformedFields.add(field);
  }

  private static ValidationFailedException malformed() {
    return new ValidationFailedException(
        List.of(new FieldError(FieldError.FIELD_BODY, FieldError.MALFORMED)));
  }
}
