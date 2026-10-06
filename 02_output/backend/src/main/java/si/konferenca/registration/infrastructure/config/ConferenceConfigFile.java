package si.konferenca.registration.infrastructure.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import si.konferenca.registration.domain.ConferenceCatalog;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.RegistrationType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the options and consents file (docs/02_contracts/conference-config.schema.json) and checks
 * the schema's rules in code. Any violation stops startup (docs/02_specification.md §5).
 */
public final class ConferenceConfigFile {

  private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
  private static final int MAX_OPTIONS = 100;
  private static final int MAX_CONSENTS = 20;

  private ConferenceConfigFile() {}

  public static ConferenceCatalog load(Path file) {
    JsonNode root;
    try {
      root = JsonMapper.builder().build().readTree(Files.readAllBytes(file));
    } catch (IOException | JacksonException e) {
      throw invalid("cannot be read as JSON");
    }
    requireObject(root, "the file", Set.of("options", "consents"), Set.of("options", "consents"));
    JsonNode options = requireArray(root.get("options"), "options", MAX_OPTIONS);
    JsonNode consents = requireArray(root.get("consents"), "consents", MAX_CONSENTS);
    List<ConferenceOption> optionList = new ArrayList<>();
    for (int i = 0; i < options.size(); i++) {
      optionList.add(option(options.get(i), "options[" + i + "]"));
    }
    List<ConsentDefinition> consentList = new ArrayList<>();
    for (int i = 0; i < consents.size(); i++) {
      consentList.add(consent(consents.get(i), "consents[" + i + "]"));
    }
    try {
      return new ConferenceCatalog(optionList, consentList);
    } catch (IllegalArgumentException e) {
      throw invalid(e.getMessage());
    }
  }

  private static ConferenceOption option(JsonNode node, String where) {
    requireObject(
        node,
        where,
        Set.of("id", "name", "category", "active"),
        Set.of("id", "name", "category", "active", "offeredTo"));
    String id = id(node.get("id"), where);
    String name = text(node.get("name"), where + ".name", 200);
    OptionCategory category =
        OptionCategory.fromValue(stringValue(node.get("category")))
            .orElseThrow(() -> invalid(where + ".category is not a known category"));
    boolean active = bool(node.get("active"), where + ".active");
    Set<RegistrationType> offeredTo = EnumSet.allOf(RegistrationType.class);
    if (node.has("offeredTo")) {
      JsonNode types = node.get("offeredTo");
      if (!types.isArray() || types.isEmpty()) {
        throw invalid(where + ".offeredTo must be a non-empty array");
      }
      offeredTo = EnumSet.noneOf(RegistrationType.class);
      for (JsonNode type : types) {
        RegistrationType value =
            RegistrationType.fromValue(stringValue(type))
                .orElseThrow(() -> invalid(where + ".offeredTo has an unknown type"));
        if (!offeredTo.add(value)) {
          throw invalid(where + ".offeredTo repeats a type");
        }
      }
    }
    return new ConferenceOption(id, name, category, active, offeredTo);
  }

  private static ConsentDefinition consent(JsonNode node, String where) {
    Set<String> keys = Set.of("id", "text", "mandatory");
    requireObject(node, where, keys, keys);
    return new ConsentDefinition(
        id(node.get("id"), where),
        text(node.get("text"), where + ".text", 2000),
        bool(node.get("mandatory"), where + ".mandatory"));
  }

  private static void requireObject(
      JsonNode node, String where, Set<String> required, Set<String> allowed) {
    if (node == null || !node.isObject()) {
      throw invalid(where + " must be an object");
    }
    for (String key : required) {
      if (!node.has(key)) {
        throw invalid(where + " has no " + key);
      }
    }
    for (String key : node.propertyNames()) {
      if (!allowed.contains(key)) {
        throw invalid(where + " has an unknown property " + key);
      }
    }
  }

  private static JsonNode requireArray(JsonNode node, String where, int max) {
    if (node == null || !node.isArray() || node.size() > max) {
      throw invalid(where + " must be an array of at most " + max + " entries");
    }
    return node;
  }

  private static String id(JsonNode node, String where) {
    String id = stringValue(node);
    if (id == null || !ID.matcher(id).matches()) {
      throw invalid(where + ".id is not a valid identifier");
    }
    return id;
  }

  private static String text(JsonNode node, String where, int max) {
    String value = stringValue(node);
    if (value == null || value.isEmpty() || value.length() > max) {
      throw invalid(where + " must be a text of 1 to " + max + " characters");
    }
    return value;
  }

  private static boolean bool(JsonNode node, String where) {
    if (node == null || !node.isBoolean()) {
      throw invalid(where + " must be true or false");
    }
    return node.booleanValue();
  }

  private static String stringValue(JsonNode node) {
    return node != null && node.isString() ? node.stringValue() : null;
  }

  private static IllegalStateException invalid(String problem) {
    return new IllegalStateException("CONFERENCE_CONFIG_FILE is invalid: " + problem);
  }
}
