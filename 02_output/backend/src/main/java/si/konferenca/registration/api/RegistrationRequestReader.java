package si.konferenca.registration.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import si.konferenca.registration.application.RegistrationCommand;
import tools.jackson.databind.JsonNode;

/**
 * Reads the createRegistration body strictly: unknown properties and wrong JSON types are a
 * malformed request; every value check is left to the validator.
 */
final class RegistrationRequestReader {

  private static final Set<String> TEXT_FIELDS =
      Set.of(
          "type",
          "firstName",
          "lastName",
          "email",
          "organization",
          "studyInstitution",
          "studyProgramme",
          "studentId",
          "captchaToken");
  private static final Set<String> KNOWN =
      Set.of(
          "type",
          "firstName",
          "lastName",
          "email",
          "organization",
          "studyInstitution",
          "studyProgramme",
          "studentId",
          "captchaToken",
          "optionIds",
          "consentGiven");

  private RegistrationRequestReader() {}

  static RegistrationCommand read(JsonNode body) {
    if (body == null || !body.isObject()) {
      throw new MalformedRequestException();
    }
    for (String name : body.propertyNames()) {
      if (!KNOWN.contains(name)) {
        throw new MalformedRequestException();
      }
      JsonNode value = body.get(name);
      if (TEXT_FIELDS.contains(name) && !value.isNull() && !value.isString()) {
        throw new MalformedRequestException();
      }
    }
    return new RegistrationCommand(
        text(body, "type"),
        text(body, "firstName"),
        text(body, "lastName"),
        text(body, "email"),
        text(body, "organization"),
        text(body, "studyInstitution"),
        text(body, "studyProgramme"),
        text(body, "studentId"),
        optionIds(body.get("optionIds")),
        consent(body.get("consentGiven")),
        text(body, "captchaToken"));
  }

  private static String text(JsonNode body, String name) {
    JsonNode value = body.get(name);
    return value == null || value.isNull() ? null : value.asString();
  }

  private static List<String> optionIds(JsonNode value) {
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isArray()) {
      throw new MalformedRequestException();
    }
    List<String> ids = new ArrayList<>();
    for (JsonNode id : value) {
      if (!id.isString()) {
        throw new MalformedRequestException();
      }
      ids.add(id.asString());
    }
    return ids;
  }

  /** A missing consent counts as not given. */
  private static boolean consent(JsonNode value) {
    if (value == null || value.isNull()) {
      return false;
    }
    if (!value.isBoolean()) {
      throw new MalformedRequestException();
    }
    return value.asBoolean();
  }
}
