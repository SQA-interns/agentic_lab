package si.konferenca.registration.acceptance.support;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Checks a JSON document against the subset of JSON Schema used by
 * 02_contracts/registration-copy.schema.json: required, additionalProperties false, const, enum,
 * string lengths, uuid and date-time formats, arrays of objects, and the type-dependent oneOf.
 */
public final class CopySchema {

  private static final Path SCHEMA =
      Path.of("..", "docs", "02_contracts", "registration-copy.schema.json");
  private static final Pattern DATE_TIME =
      Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})$");

  private CopySchema() {}

  public static JsonNode schema() {
    return Api.JSON.readTree(JsonCopies.read(SCHEMA));
  }

  /** Property names the contract allows at the top level. */
  public static Set<String> allowedProperties() {
    Set<String> names = new HashSet<>();
    schema().get("properties").propertyNames().forEach(names::add);
    return names;
  }

  /** Returns the list of violations; empty when the document is valid. */
  public static List<String> violations(JsonNode doc) {
    List<String> errors = new ArrayList<>();
    checkObject(schema(), doc, "$", errors);
    JsonNode branches = schema().get("oneOf");
    int matching = 0;
    for (JsonNode branch : branches) {
      List<String> branchErrors = new ArrayList<>();
      String type = branch.get("properties").get("type").get("const").asString();
      if (!type.equals(doc.path("type").asString(""))) {
        continue;
      }
      branch.get("required").forEach(r -> requirePresent(doc, r.asString(), branchErrors));
      JsonNode not = branch.get("not");
      List<JsonNode> forbidden = new ArrayList<>();
      if (not.has("anyOf")) {
        not.get("anyOf").forEach(forbidden::add);
      } else {
        forbidden.add(not);
      }
      for (JsonNode f : forbidden) {
        f.get("required")
            .forEach(
                r -> {
                  if (doc.has(r.asString())) {
                    branchErrors.add("$." + r.asString() + " not allowed for " + type);
                  }
                });
      }
      if (branchErrors.isEmpty()) {
        matching++;
      } else {
        errors.addAll(branchErrors);
      }
    }
    if (matching != 1) {
      errors.add("$ matches " + matching + " oneOf branches");
    }
    return errors;
  }

  private static void checkObject(JsonNode schema, JsonNode doc, String path, List<String> errors) {
    if (doc == null || !doc.isObject()) {
      errors.add(path + " is not an object");
      return;
    }
    if (schema.has("required")) {
      schema.get("required").forEach(r -> requirePresent(doc, r.asString(), errors, path));
    }
    JsonNode props = schema.get("properties");
    if (schema.path("additionalProperties").isBoolean()
        && !schema.get("additionalProperties").asBoolean()) {
      doc.propertyNames()
          .forEach(
              n -> {
                if (!props.has(n)) {
                  errors.add(path + "." + n + " is not allowed");
                }
              });
    }
    for (String name : doc.propertyNames()) {
      if (props.has(name)) {
        checkValue(props.get(name), doc.get(name), path + "." + name, errors);
      }
    }
  }

  private static void checkValue(
      JsonNode schema, JsonNode value, String path, List<String> errors) {
    if (schema.has("const") && !schema.get("const").equals(value)) {
      errors.add(path + " must be " + schema.get("const"));
    }
    if (schema.has("enum")) {
      boolean found = false;
      for (JsonNode e : schema.get("enum")) {
        found |= e.equals(value);
      }
      if (!found) {
        errors.add(path + " not in enum");
      }
    }
    String type = schema.path("type").asString("");
    switch (type) {
      case "string" -> checkString(schema, value, path, errors);
      case "array" -> {
        if (!value.isArray()) {
          errors.add(path + " is not an array");
          return;
        }
        int i = 0;
        for (JsonNode item : value) {
          checkObject(schema.get("items"), item, path + "[" + i++ + "]", errors);
        }
      }
      default -> {
        // const and enum checked above
      }
    }
  }

  private static void checkString(
      JsonNode schema, JsonNode value, String path, List<String> errors) {
    if (!value.isString()) {
      errors.add(path + " is not a string");
      return;
    }
    String s = value.asString();
    if (schema.has("minLength") && s.length() < schema.get("minLength").asInt()) {
      errors.add(path + " too short");
    }
    if (schema.has("maxLength") && s.length() > schema.get("maxLength").asInt()) {
      errors.add(path + " too long");
    }
    String format = schema.path("format").asString("");
    if ("uuid".equals(format)) {
      try {
        UUID.fromString(s);
      } catch (IllegalArgumentException e) {
        errors.add(path + " is not a uuid");
      }
    }
    if ("date-time".equals(format) && !DATE_TIME.matcher(s).matches()) {
      errors.add(path + " is not a date-time");
    }
  }

  private static void requirePresent(JsonNode doc, String name, List<String> errors) {
    requirePresent(doc, name, errors, "$");
  }

  private static void requirePresent(JsonNode doc, String name, List<String> errors, String path) {
    if (!doc.has(name) || doc.get(name).isNull()) {
      errors.add(path + "." + name + " is required");
    }
  }
}
