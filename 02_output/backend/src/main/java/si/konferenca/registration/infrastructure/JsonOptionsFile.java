package si.konferenca.registration.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import si.konferenca.registration.application.ConferenceOptions;
import si.konferenca.registration.application.OptionsCatalog;
import si.konferenca.registration.domain.OptionCategory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the options file once at start and checks it against conference-options.schema.json; an
 * invalid file stops the application (specification section 5).
 */
public final class JsonOptionsFile implements OptionsCatalog {

  private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
  private static final int MAX_OPTIONS = 100;

  private final ConferenceOptions options;

  public JsonOptionsFile(ResourceLoader loader, JsonMapper mapper, String location) {
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("CONFERENCE_OPTIONS_FILE is not set");
    }
    Resource resource =
        location.startsWith("classpath:")
            ? loader.getResource(location)
            : new FileSystemResource(Path.of(location));
    try (InputStream in = resource.getInputStream()) {
      this.options = parse(mapper.readTree(in));
    } catch (IOException | JacksonException e) {
      throw new IllegalStateException("options file cannot be read: " + location, e);
    }
  }

  @Override
  public ConferenceOptions options() {
    return options;
  }

  static ConferenceOptions parse(JsonNode root) {
    requireObject(root, "options file", Set.of("consent", "options"));
    JsonNode consent = root.get("consent");
    requireObject(consent, "consent", Set.of("id", "text"));
    String consentId = id(consent.get("id"), "consent.id");
    String consentText = text(consent.get("text"), "consent.text", 2000);

    JsonNode list = root.get("options");
    if (list == null || !list.isArray() || list.size() > MAX_OPTIONS) {
      throw invalid("options must be an array of at most " + MAX_OPTIONS + " entries");
    }
    List<ConferenceOptions.Option> options = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (JsonNode node : list) {
      requireObject(node, "option", Set.of("id", "name", "category", "active"));
      String id = id(node.get("id"), "option id");
      if (!ids.add(id)) {
        throw invalid("duplicate option id " + id);
      }
      String name = text(node.get("name"), "option " + id + " name", 200);
      JsonNode category = node.get("category");
      OptionCategory parsed =
          category != null && category.isString()
              ? OptionCategory.fromValue(category.asString()).orElse(null)
              : null;
      if (parsed == null) {
        throw invalid("option " + id + " has an unknown category");
      }
      JsonNode active = node.get("active");
      if (active == null || !active.isBoolean()) {
        throw invalid("option " + id + " needs active true or false");
      }
      options.add(new ConferenceOptions.Option(id, name, parsed, active.asBoolean()));
    }
    return new ConferenceOptions(new ConferenceOptions.Consent(consentId, consentText), options);
  }

  private static void requireObject(JsonNode node, String what, Set<String> fields) {
    if (node == null || !node.isObject()) {
      throw invalid(what + " must be an object");
    }
    for (String name : node.propertyNames()) {
      if (!fields.contains(name)) {
        throw invalid(what + " has an unknown property " + name);
      }
    }
    for (String field : fields) {
      if (!node.has(field)) {
        throw invalid(what + " misses " + field);
      }
    }
  }

  private static String id(JsonNode node, String what) {
    if (node == null || !node.isString() || !ID.matcher(node.asString()).matches()) {
      throw invalid(what + " must match " + ID.pattern());
    }
    return node.asString();
  }

  private static String text(JsonNode node, String what, int max) {
    if (node == null || !node.isString() || node.asString().isEmpty()) {
      throw invalid(what + " must be a non-empty string");
    }
    String value = node.asString();
    if (value.codePointCount(0, value.length()) > max) {
      throw invalid(what + " is longer than " + max + " characters");
    }
    return value;
  }

  private static IllegalStateException invalid(String message) {
    return new IllegalStateException("invalid options file: " + message);
  }
}
