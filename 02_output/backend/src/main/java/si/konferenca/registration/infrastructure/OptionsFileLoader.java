package si.konferenca.registration.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.OptionsCatalog;
import si.konferenca.registration.domain.RegistrationType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the options configuration file (options-config.schema.json) at startup (AR-04, S-5). Any
 * violation stops the application with a message naming the problem.
 */
public final class OptionsFileLoader {

  private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
  private static final Set<String> ROOT_KEYS = Set.of("options", "categoryLimits", "consents");
  private static final Set<String> OPTION_KEYS =
      Set.of("id", "name", "category", "active", "registrationTypes");
  private static final Set<String> CONSENT_KEYS = Set.of("id", "text", "mandatory");

  private OptionsFileLoader() {}

  public static OptionsCatalog load(Path file) {
    JsonNode root;
    try {
      root = JsonMapper.builder().build().readTree(Files.readAllBytes(file));
    } catch (IOException | JacksonException e) {
      throw new IllegalStateException("Options file cannot be read or is not valid JSON", e);
    }
    require(root.isObject(), "the options file must contain a JSON object");
    onlyKeys(root, ROOT_KEYS, "options file");
    return new OptionsCatalog(options(root.path("options")), limits(root), consents(root));
  }

  private static List<ConferenceOption> options(JsonNode array) {
    require(array.isArray(), "'options' must be an array");
    List<ConferenceOption> options = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (JsonNode o : array) {
      require(o.isObject(), "each option must be an object");
      onlyKeys(o, OPTION_KEYS, "option");
      String id = text(o, "id");
      require(ID.matcher(id).matches(), "option id '" + id + "' is not a valid identifier");
      require(ids.add(id), "option id '" + id + "' is used twice");
      String name = text(o, "name");
      require(name.length() <= 200, "option '" + id + "' has a name longer than 200 characters");
      require(o.path("active").isBoolean(), "option '" + id + "' needs a boolean 'active'");
      options.add(
          new ConferenceOption(
              id,
              name,
              category(o.path("category").asString(""), "option '" + id + "'"),
              o.path("active").asBoolean(),
              types(o.path("registrationTypes"), id)));
    }
    return options;
  }

  private static Set<RegistrationType> types(JsonNode node, String optionId) {
    if (node.isMissingNode()) {
      return EnumSet.allOf(RegistrationType.class);
    }
    require(
        node.isArray() && !node.isEmpty(),
        "option '" + optionId + "' has an empty or invalid 'registrationTypes'");
    Set<RegistrationType> types = EnumSet.noneOf(RegistrationType.class);
    for (JsonNode t : node) {
      String name = t.asString("");
      RegistrationType type =
          RegistrationType.parse(name)
              .filter(p -> p.name().equals(name))
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "option '" + optionId + "' has an unknown registration type"));
      require(types.add(type), "option '" + optionId + "' repeats a registration type");
    }
    return types;
  }

  private static Map<Category, Integer> limits(JsonNode root) {
    Map<Category, Integer> limits = new EnumMap<>(Category.class);
    JsonNode node = root.path("categoryLimits");
    if (node.isMissingNode()) {
      return limits;
    }
    require(node.isObject(), "'categoryLimits' must be an object");
    for (Map.Entry<String, JsonNode> e : node.properties()) {
      require(
          e.getValue().isInt() && e.getValue().asInt() >= 0,
          "category limit for '" + e.getKey() + "' must be a non-negative integer");
      limits.put(category(e.getKey(), "categoryLimits"), e.getValue().asInt());
    }
    return limits;
  }

  private static List<ConsentDefinition> consents(JsonNode root) {
    JsonNode array = root.path("consents");
    require(array.isArray() && !array.isEmpty(), "'consents' must be a non-empty array");
    List<ConsentDefinition> consents = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (JsonNode c : array) {
      require(c.isObject(), "each consent must be an object");
      onlyKeys(c, CONSENT_KEYS, "consent");
      String id = text(c, "id");
      require(ID.matcher(id).matches(), "consent id '" + id + "' is not a valid identifier");
      require(ids.add(id), "consent id '" + id + "' is used twice");
      String wording = text(c, "text");
      require(wording.length() <= 1000, "consent '" + id + "' is longer than 1000 characters");
      require(c.path("mandatory").isBoolean(), "consent '" + id + "' needs a boolean 'mandatory'");
      consents.add(new ConsentDefinition(id, wording, c.path("mandatory").asBoolean()));
    }
    require(
        consents.stream().anyMatch(ConsentDefinition::mandatory),
        "at least one consent must be mandatory (BR-05)");
    return consents;
  }

  private static Category category(String value, String where) {
    for (Category c : Category.values()) {
      if (c.name().equals(value)) {
        return c;
      }
    }
    throw new IllegalStateException(where + " has an unknown category '" + value + "'");
  }

  private static String text(JsonNode node, String key) {
    JsonNode value = node.path(key);
    require(
        value.isString() && !value.asString().isBlank(),
        "'" + key + "' must be a non-empty string");
    return value.asString();
  }

  private static void onlyKeys(JsonNode node, Set<String> allowed, String where) {
    for (String key : node.propertyNames()) {
      require(allowed.contains(key), where + " has an unknown property '" + key + "'");
    }
  }

  private static void require(boolean condition, String problem) {
    if (!condition) {
      throw new IllegalStateException("Invalid options file: " + problem);
    }
  }
}
