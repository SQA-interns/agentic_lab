package si.konferenca.registration.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import si.konferenca.registration.application.OptionCatalog;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.settings.AppProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads and validates the options file once at startup
 * (docs/02_contracts/conference-options.schema.json). Any violation stops the application (AR-04,
 * AC-003-03).
 */
@Component
public final class OptionsFileCatalog implements OptionCatalog {

  private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
  private static final Set<String> OPTION_FIELDS = Set.of("id", "name", "category", "active");
  private static final Set<String> CONSENT_FIELDS = Set.of("id", "text", "required");

  private final Map<String, ConferenceOption> options;
  private final List<ConsentDefinition> consents;

  public OptionsFileCatalog(AppProperties properties, ResourceLoader resourceLoader) {
    JsonNode root = read(resourceLoader.getResource(properties.optionsFile()));
    requireFields(root, Set.of("options", "consents"), "file");
    this.options = parseOptions(root.get("options"));
    this.consents = parseConsents(root.get("consents"));
  }

  @Override
  public List<ConferenceOption> activeOptions() {
    return options.values().stream().filter(ConferenceOption::active).toList();
  }

  @Override
  public Optional<ConferenceOption> find(String id) {
    return Optional.ofNullable(options.get(id));
  }

  @Override
  public List<ConsentDefinition> consents() {
    return consents;
  }

  private static JsonNode read(Resource resource) {
    if (!resource.exists()) {
      throw invalid("options file not found: " + resource.getDescription());
    }
    try (InputStream in = resource.getInputStream()) {
      return JsonMapper.builder().build().readTree(in);
    } catch (IOException | JacksonException e) {
      throw invalid("options file is not valid JSON: " + e.getMessage());
    }
  }

  private static Map<String, ConferenceOption> parseOptions(JsonNode array) {
    requireArray(array, "options");
    Map<String, ConferenceOption> result = new LinkedHashMap<>();
    for (JsonNode node : array) {
      requireFields(node, OPTION_FIELDS, "option");
      String id = id(node, "option");
      String name = text(node, "name", 200, "option " + id);
      String categoryCode = text(node, "category", 10, "option " + id);
      OptionCategory category =
          OptionCategory.fromCode(categoryCode)
              .orElseThrow(() -> invalid("option " + id + " has unknown category " + categoryCode));
      JsonNode active = node.get("active");
      if (!active.isBoolean()) {
        throw invalid("option " + id + ": active must be true or false");
      }
      if (result.put(id, new ConferenceOption(id, name, category, active.asBoolean())) != null) {
        throw invalid("duplicate option id " + id);
      }
    }
    return result;
  }

  private static List<ConsentDefinition> parseConsents(JsonNode array) {
    requireArray(array, "consents");
    List<ConsentDefinition> result = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (JsonNode node : array) {
      requireFields(node, CONSENT_FIELDS, "consent");
      String id = id(node, "consent");
      String text = text(node, "text", 1000, "consent " + id);
      JsonNode required = node.get("required");
      if (!required.isBoolean()) {
        throw invalid("consent " + id + ": required must be true or false");
      }
      if (!ids.add(id)) {
        throw invalid("duplicate consent id " + id);
      }
      result.add(new ConsentDefinition(id, text, required.asBoolean()));
    }
    return List.copyOf(result);
  }

  private static void requireArray(JsonNode node, String name) {
    if (node == null || !node.isArray()) {
      throw invalid(name + " must be an array");
    }
  }

  private static void requireFields(JsonNode node, Set<String> fields, String what) {
    if (node == null || !node.isObject()) {
      throw invalid(what + " must be an object");
    }
    Set<String> present = new HashSet<>(node.propertyNames());
    if (!present.equals(fields)) {
      throw invalid(what + " must have exactly the fields " + fields + " but has " + present);
    }
  }

  private static String id(JsonNode node, String what) {
    JsonNode id = node.get("id");
    if (!id.isString() || !ID.matcher(id.asString()).matches()) {
      throw invalid(what + " id must match " + ID.pattern());
    }
    return id.asString();
  }

  private static String text(JsonNode node, String field, int max, String what) {
    JsonNode value = node.get(field);
    if (!value.isString() || value.asString().isBlank() || value.asString().length() > max) {
      throw invalid(what + ": " + field + " must be text of 1 to " + max + " characters");
    }
    return value.asString();
  }

  private static IllegalStateException invalid(String message) {
    return new IllegalStateException("Invalid conference options configuration: " + message);
  }
}
