package si.konferenca.registration.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import si.konferenca.registration.service.ErrorCode;
import si.konferenca.registration.service.RegistrationRejectedException;
import si.konferenca.registration.service.RegistrationRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a `RegistrationRequest` strictly: an object with known properties only, string values or
 * null, string arrays for the id lists, no duplicate keys. Anything else is MALFORMED_REQUEST.
 */
final class RegistrationRequestParser {

  private static final Set<String> PROPERTIES =
      Set.of(
          "type",
          "firstName",
          "lastName",
          "email",
          "organization",
          "studyInstitution",
          "studyProgramme",
          "studentId",
          "optionIds",
          "consentIds",
          "antiAutomationToken");

  private static final JsonMapper MAPPER =
      JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

  private RegistrationRequestParser() {}

  static RegistrationRequest parse(byte[] body) {
    JsonNode root;
    try {
      root = MAPPER.readTree(body);
    } catch (JacksonException e) {
      throw malformed();
    }
    if (root == null || !root.isObject()) {
      throw malformed();
    }
    for (String name : root.propertyNames()) {
      if (!PROPERTIES.contains(name)) {
        throw malformed();
      }
    }
    return new RegistrationRequest(
        text(root, "type"),
        text(root, "firstName"),
        text(root, "lastName"),
        text(root, "email"),
        text(root, "organization"),
        text(root, "studyInstitution"),
        text(root, "studyProgramme"),
        text(root, "studentId"),
        strings(root, "optionIds"),
        strings(root, "consentIds"),
        text(root, "antiAutomationToken"));
  }

  private static String text(JsonNode root, String name) {
    JsonNode value = root.get(name);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isString()) {
      throw malformed();
    }
    return value.asString();
  }

  private static List<String> strings(JsonNode root, String name) {
    JsonNode value = root.get(name);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isArray()) {
      throw malformed();
    }
    List<String> values = new ArrayList<>();
    for (JsonNode element : value) {
      if (!element.isString()) {
        throw malformed();
      }
      values.add(element.asString());
    }
    return values;
  }

  private static RegistrationRejectedException malformed() {
    return new RegistrationRejectedException(ErrorCode.MALFORMED_REQUEST);
  }
}
