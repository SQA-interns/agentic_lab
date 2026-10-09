package si.konferenca.registration.config;

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
import si.konferenca.registration.domain.ConferenceCatalogue;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Consent;
import si.konferenca.registration.domain.RegistrationType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the options file (docs/02_contracts/conference-config.schema.json, AR-04) and refuses an
 * invalid one with a message naming the entry.
 */
public final class ConferenceConfigLoader {

  private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}$");
  private static final Set<String> ROOT_KEYS =
      Set.of("maxSelectionsPerCategory", "options", "consents");
  private static final Set<String> OPTION_KEYS =
      Set.of("id", "name", "category", "active", "availableTo");
  private static final Set<String> CONSENT_KEYS = Set.of("id", "text", "mandatory");
  private static final int MAX_OPTIONS = 200;
  private static final int MAX_CONSENTS = 20;

  private ConferenceConfigLoader() {}

  public static ConferenceCatalogue load(Path file) {
    JsonNode root;
    try {
      root = JsonMapper.builder().build().readTree(Files.readString(file));
    } catch (IOException | JacksonException e) {
      throw invalid("cannot read " + file.getFileName() + " as JSON");
    }
    if (root == null || !root.isObject()) {
      throw invalid("root must be an object");
    }
    onlyKeys(root, ROOT_KEYS, "root");
    return new ConferenceCatalogue(options(root), limits(root), consents(root));
  }

  private static List<ConferenceOption> options(JsonNode root) {
    JsonNode array = root.get("options");
    if (array == null || !array.isArray() || array.size() > MAX_OPTIONS) {
      throw invalid("options must be an array of at most " + MAX_OPTIONS);
    }
    List<ConferenceOption> options = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < array.size(); i++) {
      JsonNode node = array.get(i);
      String where = "options[" + i + "]";
      if (!node.isObject()) {
        throw invalid(where + " must be an object");
      }
      onlyKeys(node, OPTION_KEYS, where);
      String id = id(node, where);
      if (!ids.add(id)) {
        throw invalid(where + " repeats id " + id);
      }
      String name = text(node, "name", 200, where);
      JsonNode categoryNode = node.get("category");
      Category category =
          category(
              categoryNode != null && categoryNode.isString() ? categoryNode.asString() : null,
              where);
      JsonNode active = node.get("active");
      if (active == null || !active.isBoolean()) {
        throw invalid(where + ".active must be true or false");
      }
      options.add(new ConferenceOption(id, name, category, active.asBoolean(), types(node, where)));
    }
    return options;
  }

  private static Set<RegistrationType> types(JsonNode node, String where) {
    JsonNode array = node.get("availableTo");
    if (array == null || !array.isArray() || array.isEmpty()) {
      throw invalid(where + ".availableTo must list at least one type");
    }
    Set<RegistrationType> types = EnumSet.noneOf(RegistrationType.class);
    for (JsonNode type : array) {
      RegistrationType parsed =
          type.isString() ? RegistrationType.fromName(type.asString()).orElse(null) : null;
      if (parsed == null || !types.add(parsed)) {
        throw invalid(where + ".availableTo has an unknown or repeated type");
      }
    }
    return types;
  }

  private static Map<Category, Integer> limits(JsonNode root) {
    Map<Category, Integer> limits = new EnumMap<>(Category.class);
    JsonNode node = root.get("maxSelectionsPerCategory");
    if (node == null) {
      return limits;
    }
    if (!node.isObject()) {
      throw invalid("maxSelectionsPerCategory must be an object");
    }
    for (String key : node.propertyNames()) {
      String where = "maxSelectionsPerCategory." + key;
      Category category = category(key, where);
      JsonNode value = node.get(key);
      if (!value.isInt() || value.asInt() < 0) {
        throw invalid(where + " must be a whole number, 0 or more");
      }
      limits.put(category, value.asInt());
    }
    return limits;
  }

  private static List<Consent> consents(JsonNode root) {
    JsonNode array = root.get("consents");
    if (array == null || !array.isArray() || array.isEmpty() || array.size() > MAX_CONSENTS) {
      throw invalid("consents must list 1 to " + MAX_CONSENTS + " consents");
    }
    List<Consent> consents = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < array.size(); i++) {
      JsonNode node = array.get(i);
      String where = "consents[" + i + "]";
      if (!node.isObject()) {
        throw invalid(where + " must be an object");
      }
      onlyKeys(node, CONSENT_KEYS, where);
      String id = id(node, where);
      if (!ids.add(id)) {
        throw invalid(where + " repeats id " + id);
      }
      JsonNode mandatory = node.get("mandatory");
      if (mandatory != null && !mandatory.isBoolean()) {
        throw invalid(where + ".mandatory must be true or false");
      }
      consents.add(
          new Consent(
              id, text(node, "text", 2000, where), mandatory == null || mandatory.asBoolean()));
    }
    return consents;
  }

  private static Category category(String value, String where) {
    for (Category category : Category.values()) {
      if (category.name().equals(value)) {
        return category;
      }
    }
    throw invalid(where + " has an unknown category");
  }

  private static String id(JsonNode node, String where) {
    JsonNode id = node.get("id");
    if (id == null || !id.isString() || !ID.matcher(id.asString()).matches()) {
      throw invalid(where + ".id is missing or not a valid identifier");
    }
    return id.asString();
  }

  private static String text(JsonNode node, String key, int max, String where) {
    JsonNode value = node.get(key);
    if (value == null
        || !value.isString()
        || value.asString().isBlank()
        || value.asString().length() > max) {
      throw invalid(where + "." + key + " must be text of 1 to " + max + " characters");
    }
    return value.asString();
  }

  private static void onlyKeys(JsonNode node, Set<String> allowed, String where) {
    for (String key : node.propertyNames()) {
      if (!allowed.contains(key)) {
        throw invalid(where + " has an unknown property " + key);
      }
    }
  }

  private static IllegalStateException invalid(String message) {
    return new IllegalStateException("Invalid conference configuration: " + message);
  }
}
