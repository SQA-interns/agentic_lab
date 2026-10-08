package si.konferenca.registration.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Consent;
import si.konferenca.registration.domain.OptionCatalogue;
import si.konferenca.registration.domain.RegistrationType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the conference options file (conference-options.schema.json, AR-04). Any deviation from the
 * schema stops the application at startup.
 */
@Component
public class OptionsFileReader {

  static final String LOCAL_DEFAULT = "options/conference-options.local.json";

  private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
  private static final int MAX_LIMIT = 50;
  private static final Set<String> ROOT = Set.of("categories", "options", "consents");
  private static final Set<String> OPTION_REQUIRED = Set.of("id", "name", "category", "active");
  private static final Set<String> OPTION_ALLOWED =
      Set.of("id", "name", "category", "active", "registrationTypes");
  private static final Set<String> CONSENT = Set.of("id", "text", "mandatory");

  private final AppProperties properties;
  private final JsonMapper mapper = JsonMapper.builder().build();

  public OptionsFileReader(AppProperties properties) {
    this.properties = properties;
  }

  /** Reads the configured file, or the bundled local file outside production. */
  public OptionCatalogue read() {
    String file = properties.optionsFile();
    try {
      if (file != null && !file.isBlank()) {
        try (InputStream in = Files.newInputStream(Path.of(file))) {
          return parse(mapper.readTree(in));
        }
      }
      if (properties.production()) {
        throw new IllegalStateException("CONFERENCE_OPTIONS_FILE is required in production");
      }
      try (InputStream in = new ClassPathResource(LOCAL_DEFAULT).getInputStream()) {
        return parse(mapper.readTree(in));
      }
    } catch (IOException | JacksonException e) {
      throw new IllegalStateException("conference options file cannot be read: " + e.getMessage());
    }
  }

  static OptionCatalogue parse(JsonNode root) {
    requireObject(root, "root", ROOT, ROOT);
    Map<Category, Integer> limits = new EnumMap<>(Category.class);
    JsonNode categories = root.path("categories");
    requireObject(categories, "categories", names(Category.values()), Set.of());
    for (Iterator<Map.Entry<String, JsonNode>> it = categories.properties().iterator();
        it.hasNext(); ) {
      Map.Entry<String, JsonNode> e = it.next();
      JsonNode value = e.getValue();
      if (!value.isIntegralNumber() || value.asInt() < 0 || value.asInt() > MAX_LIMIT) {
        throw invalid("categories." + e.getKey() + " must be an integer from 0 to " + MAX_LIMIT);
      }
      limits.put(Category.valueOf(e.getKey()), value.asInt());
    }
    List<ConferenceOption> options = new ArrayList<>();
    for (JsonNode o : requireArray(root.path("options"), "options")) {
      requireObject(o, "option", OPTION_ALLOWED, OPTION_REQUIRED);
      options.add(
          new ConferenceOption(
              id(o.path("id"), "option.id"),
              text(o.path("name"), "option.name", 200),
              Category.valueOf(oneOf(o.path("category"), "option.category", Category.values())),
              bool(o.path("active"), "option.active"),
              types(o.path("registrationTypes"))));
    }
    List<Consent> consents = new ArrayList<>();
    JsonNode consentArray = requireArray(root.path("consents"), "consents");
    if (consentArray.isEmpty()) {
      throw invalid("consents must not be empty");
    }
    for (JsonNode c : consentArray) {
      requireObject(c, "consent", CONSENT, CONSENT);
      consents.add(
          new Consent(
              id(c.path("id"), "consent.id"),
              text(c.path("text"), "consent.text", 1000),
              bool(c.path("mandatory"), "consent.mandatory")));
    }
    try {
      return new OptionCatalogue(limits, options, consents);
    } catch (IllegalArgumentException e) {
      throw invalid(e.getMessage());
    }
  }

  private static Set<RegistrationType> types(JsonNode node) {
    if (node.isMissingNode()) {
      return EnumSet.allOf(RegistrationType.class);
    }
    Set<RegistrationType> types = EnumSet.noneOf(RegistrationType.class);
    for (JsonNode t : requireArray(node, "option.registrationTypes")) {
      if (!types.add(
          RegistrationType.valueOf(oneOf(t, "registrationTypes", RegistrationType.values())))) {
        throw invalid("option.registrationTypes has duplicates");
      }
    }
    if (types.isEmpty()) {
      throw invalid("option.registrationTypes must not be empty");
    }
    return types;
  }

  private static void requireObject(
      JsonNode node, String where, Set<String> allowed, Set<String> required) {
    if (!node.isObject()) {
      throw invalid(where + " must be an object");
    }
    for (String name : node.propertyNames()) {
      if (!allowed.contains(name)) {
        throw invalid(where + " has unknown property " + name);
      }
    }
    for (String name : required) {
      if (!node.has(name)) {
        throw invalid(where + " lacks " + name);
      }
    }
  }

  private static JsonNode requireArray(JsonNode node, String where) {
    if (!node.isArray()) {
      throw invalid(where + " must be an array");
    }
    return node;
  }

  private static String id(JsonNode node, String where) {
    if (!node.isString() || !ID.matcher(node.asString()).matches()) {
      throw invalid(where + " must match " + ID.pattern());
    }
    return node.asString();
  }

  private static String text(JsonNode node, String where, int max) {
    if (!node.isString() || node.asString().isEmpty() || node.asString().length() > max) {
      throw invalid(where + " must be a string of 1 to " + max + " characters");
    }
    return node.asString();
  }

  private static boolean bool(JsonNode node, String where) {
    if (!node.isBoolean()) {
      throw invalid(where + " must be true or false");
    }
    return node.asBoolean();
  }

  private static String oneOf(JsonNode node, String where, Enum<?>[] values) {
    if (node.isString() && names(values).contains(node.asString())) {
      return node.asString();
    }
    throw invalid(where + " must be one of " + names(values));
  }

  private static Set<String> names(Enum<?>[] values) {
    Set<String> names = new LinkedHashSet<>();
    for (Enum<?> value : values) {
      names.add(value.name());
    }
    return names;
  }

  private static IllegalStateException invalid(String message) {
    return new IllegalStateException("invalid conference options file: " + message);
  }
}
