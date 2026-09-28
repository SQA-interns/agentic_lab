package org.example.conference.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses and validates the YAML catalog file. Unknown top-level keys, duplicate IDs, bad ID
 * syntax, blank or control-character names are rejected so a misconfiguration fails at startup.
 */
public final class CatalogLoader {

  private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
  private static final Pattern CONTROL = Pattern.compile("\\p{Cc}");
  private static final int MAX_NAME = 200;
  private static final int MAX_CONSENT_TEXT = 2000;
  private static final Set<String> TOP_LEVEL_KEYS =
      Set.of(
          "conferenceName", "workshops", "events", "meals", "otherActivities", "consents");

  private CatalogLoader() {}

  public static Catalog load(Path path) {
    try (InputStream in = Files.newInputStream(path)) {
      return parse(new ObjectMapper(new YAMLFactory()).readTree(in));
    } catch (IOException e) {
      throw new InvalidCatalogException("Catalog file cannot be read: " + path, e);
    }
  }

  static Catalog parse(JsonNode root) {
    if (root == null || !root.isObject()) {
      throw new InvalidCatalogException("Catalog must be a mapping");
    }
    Iterator<String> names = root.fieldNames();
    while (names.hasNext()) {
      String key = names.next();
      if (!TOP_LEVEL_KEYS.contains(key)) {
        throw new InvalidCatalogException("Unknown catalog key: " + key);
      }
    }
    String conferenceName = text(root.get("conferenceName"), "conferenceName", MAX_NAME);
    Map<OptionGroup, List<CatalogOption>> options = new EnumMap<>(OptionGroup.class);
    for (OptionGroup group : OptionGroup.values()) {
      options.put(group, parseOptions(root.get(group.key()), group.key()));
    }
    return new Catalog(conferenceName, options, parseConsents(root.get("consents")));
  }

  private static List<CatalogOption> parseOptions(JsonNode node, String group) {
    List<CatalogOption> result = new ArrayList<>();
    if (node == null || node.isNull()) {
      return result;
    }
    if (!node.isArray()) {
      throw new InvalidCatalogException(group + " must be a list");
    }
    Set<String> ids = new HashSet<>();
    for (JsonNode item : node) {
      String id = id(item.get("id"), group);
      if (!ids.add(id)) {
        throw new InvalidCatalogException("Duplicate option id in " + group + ": " + id);
      }
      String name = text(item.get("name"), group + "." + id + ".name", MAX_NAME);
      JsonNode active = item.get("active");
      if (active == null || !active.isBoolean()) {
        throw new InvalidCatalogException(group + "." + id + ".active must be true or false");
      }
      result.add(new CatalogOption(id, name, active.booleanValue()));
    }
    return result;
  }

  private static List<ConsentDefinition> parseConsents(JsonNode node) {
    List<ConsentDefinition> result = new ArrayList<>();
    if (node == null || node.isNull()) {
      return result;
    }
    if (!node.isArray()) {
      throw new InvalidCatalogException("consents must be a list");
    }
    Set<String> ids = new HashSet<>();
    for (JsonNode item : node) {
      String id = id(item.get("id"), "consents");
      if (!ids.add(id)) {
        throw new InvalidCatalogException("Duplicate consent id: " + id);
      }
      String text = text(item.get("text"), "consents." + id + ".text", MAX_CONSENT_TEXT);
      JsonNode required = item.get("required");
      if (required == null || !required.isBoolean()) {
        throw new InvalidCatalogException("consents." + id + ".required must be true or false");
      }
      result.add(new ConsentDefinition(id, text, required.booleanValue()));
    }
    return result;
  }

  private static String id(JsonNode node, String context) {
    if (node == null || !node.isTextual() || !ID.matcher(node.asText()).matches()) {
      throw new InvalidCatalogException(
          context + ": option id must match " + ID.pattern() + " (lowercase, digits, hyphen)");
    }
    return node.asText();
  }

  private static String text(JsonNode node, String context, int max) {
    if (node == null || !node.isTextual()) {
      throw new InvalidCatalogException(context + " must be text");
    }
    String value = node.asText().strip();
    if (value.isEmpty() || value.length() > max || CONTROL.matcher(value).find()) {
      throw new InvalidCatalogException(context + " must be non-blank, <= " + max + " chars");
    }
    return value;
  }
}
