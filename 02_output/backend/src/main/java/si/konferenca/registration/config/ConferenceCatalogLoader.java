package si.konferenca.registration.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceCatalog;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.RegistrationType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the conference configuration file and checks it against the rules of {@code
 * conference-config.schema.json}; an invalid file stops the application (AR-04).
 */
public final class ConferenceCatalogLoader {

  private static final Pattern IDENTIFIER = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
  private static final Set<String> ROOT = Set.of("categoryLimits", "consents", "options");
  private static final Set<String> OPTION =
      Set.of("id", "displayName", "category", "active", "availableTo");
  private static final Set<String> CONSENT = Set.of("id", "text");
  private static final String BUNDLED = "/conference-config.json";

  private final JsonMapper mapper = JsonMapper.builder().build();

  /** Loads the file, or the bundled example when no file is configured. */
  public ConferenceCatalog load(String file) {
    try (InputStream in =
        file == null || file.isBlank()
            ? ConferenceCatalogLoader.class.getResourceAsStream(BUNDLED)
            : Files.newInputStream(Path.of(file))) {
      if (in == null) {
        throw new InvalidConfigurationException("bundled conference configuration missing");
      }
      return parse(mapper.readTree(in));
    } catch (IOException | JacksonException e) {
      throw new InvalidConfigurationException(
          "CONFERENCE_CONFIG_FILE cannot be read as JSON: " + e.getClass().getSimpleName());
    }
  }

  ConferenceCatalog parse(JsonNode root) {
    requireObject(root, "root", ROOT);
    Map<Category, Integer> limits = new EnumMap<>(Category.class);
    JsonNode limitNode = root.path("categoryLimits");
    if (!limitNode.isMissingNode()) {
      requireObject(limitNode, "categoryLimits", names(Category.class));
      for (Map.Entry<String, JsonNode> entry : limitNode.properties()) {
        JsonNode value = entry.getValue();
        if (!value.isInt() || value.asInt() < 1 || value.asInt() > 100) {
          throw invalid("categoryLimits." + entry.getKey() + " must be an integer 1..100");
        }
        limits.put(Category.valueOf(entry.getKey()), value.asInt());
      }
    }
    return new ConferenceCatalog(
        options(root.path("options")), consents(root.path("consents")), limits);
  }

  private List<ConsentDefinition> consents(JsonNode array) {
    if (!array.isArray() || array.isEmpty()) {
      throw invalid("consents must be a non-empty array");
    }
    List<ConsentDefinition> consents = new ArrayList<>();
    for (JsonNode node : array) {
      requireObject(node, "consent", CONSENT);
      String text = text(node, "text", 1000);
      consents.add(new ConsentDefinition(identifier(node), text));
    }
    if (consents.stream().map(ConsentDefinition::id).distinct().count() != consents.size()) {
      throw invalid("consent ids must be unique");
    }
    return consents;
  }

  private List<ConferenceOption> options(JsonNode array) {
    if (!array.isArray()) {
      throw invalid("options must be an array");
    }
    List<ConferenceOption> options = new ArrayList<>();
    for (JsonNode node : array) {
      requireObject(node, "option", OPTION);
      String id = identifier(node);
      String displayName = text(node, "displayName", 200);
      Category category = enumValue(Category.class, node.path("category"), "category of " + id);
      if (!node.path("active").isBoolean()) {
        throw invalid("active of " + id + " must be true or false");
      }
      Set<RegistrationType> availableTo = EnumSet.allOf(RegistrationType.class);
      JsonNode types = node.path("availableTo");
      if (!types.isMissingNode()) {
        if (!types.isArray() || types.isEmpty()) {
          throw invalid("availableTo of " + id + " must be a non-empty array");
        }
        availableTo = EnumSet.noneOf(RegistrationType.class);
        for (JsonNode type : types) {
          if (!availableTo.add(enumValue(RegistrationType.class, type, "availableTo of " + id))) {
            throw invalid("availableTo of " + id + " repeats a type");
          }
        }
      }
      options.add(
          new ConferenceOption(
              id, displayName, category, node.path("active").asBoolean(), availableTo));
    }
    try {
      new ConferenceCatalog(options, List.of(), Map.of());
    } catch (IllegalArgumentException e) {
      throw invalid(e.getMessage());
    }
    return options;
  }

  private static void requireObject(JsonNode node, String where, Set<String> allowed) {
    if (!node.isObject()) {
      throw invalid(where + " must be an object");
    }
    for (String name : node.propertyNames()) {
      if (!allowed.contains(name)) {
        throw invalid(where + " has an unknown property " + name);
      }
    }
  }

  private static String identifier(JsonNode node) {
    JsonNode id = node.path("id");
    if (!id.isString() || !IDENTIFIER.matcher(id.asString()).matches()) {
      throw invalid("id " + id + " must match " + IDENTIFIER.pattern());
    }
    return id.asString();
  }

  private static String text(JsonNode node, String property, int maxLength) {
    JsonNode value = node.path(property);
    if (!value.isString() || value.asString().isEmpty() || value.asString().length() > maxLength) {
      throw invalid(property + " must be a string of 1.." + maxLength + " characters");
    }
    return value.asString();
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode value, String where) {
    if (value.isString()) {
      for (E constant : type.getEnumConstants()) {
        if (constant.name().equals(value.asString())) {
          return constant;
        }
      }
    }
    throw invalid(where + " must be one of " + names(type));
  }

  private static <E extends Enum<E>> Set<String> names(Class<E> type) {
    Set<String> names = new java.util.LinkedHashSet<>();
    for (E constant : type.getEnumConstants()) {
      names.add(constant.name());
    }
    return names;
  }

  private static InvalidConfigurationException invalid(String message) {
    return new InvalidConfigurationException("conference configuration: " + message);
  }
}
