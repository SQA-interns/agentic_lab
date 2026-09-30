package lab.conference.options;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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
 * Reads and validates the catalog file (catalog-config.schema.json). Any violation throws, so the
 * backend refuses to start instead of serving a partial catalog (D-19).
 */
public final class CatalogLoader {

  static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
  private static final String CONSENT = "consent";
  private static final Set<String> TOP_LEVEL = Set.of("conferenceTitle", CONSENT, "groups");
  private static final Set<String> CONSENT_FIELDS = Set.of("id", "text", "required");
  private static final Set<String> OPTION_FIELDS = Set.of("id", "name", "active");
  private final ObjectMapper json = new ObjectMapper();

  public Catalog load(Path path) {
    if (path == null || !Files.isRegularFile(path)) {
      throw new InvalidCatalogException("catalog file not found: CONFERENCE_CONFIG_PATH");
    }
    JsonNode root;
    try {
      root = json.readTree(Files.readAllBytes(path));
    } catch (JsonProcessingException e) {
      throw new InvalidCatalogException("catalog file is not valid JSON", e);
    } catch (IOException e) {
      throw new InvalidCatalogException("catalog file cannot be read", e);
    }
    return parse(root);
  }

  Catalog parse(JsonNode root) {
    requireObject(root, "catalog");
    onlyFields(root, TOP_LEVEL, "catalog");
    String title = text(root, "conferenceTitle", 200);
    ConsentFixture consent = root.has(CONSENT) ? consent(root.get(CONSENT)) : null;
    JsonNode groupsNode = root.get("groups");
    requireObject(groupsNode, "groups");
    Map<GroupId, List<CatalogOption>> groups = new EnumMap<>(GroupId.class);
    Set<String> seen = new HashSet<>();
    for (Iterator<String> it = groupsNode.fieldNames(); it.hasNext(); ) {
      String key = it.next();
      GroupId group =
          GroupId.fromKey(key)
              .orElseThrow(() -> new InvalidCatalogException("unknown option group: " + key));
      groups.put(group, options(groupsNode.get(key), key, seen));
    }
    for (GroupId g : GroupId.values()) {
      if (!groups.containsKey(g)) {
        throw new InvalidCatalogException("missing option group: " + g.key());
      }
    }
    return new Catalog(title, consent, groups);
  }

  private List<CatalogOption> options(JsonNode array, String group, Set<String> seen) {
    if (array == null || !array.isArray()) {
      throw new InvalidCatalogException("group " + group + " must be an array");
    }
    List<CatalogOption> list = new ArrayList<>();
    for (JsonNode node : array) {
      requireObject(node, "option in " + group);
      onlyFields(node, OPTION_FIELDS, "option in " + group);
      String id = text(node, "id", 64);
      if (!ID.matcher(id).matches()) {
        throw new InvalidCatalogException("invalid option id in " + group + ": " + id);
      }
      if (!seen.add(id)) {
        throw new InvalidCatalogException("duplicate option id: " + id);
      }
      list.add(new CatalogOption(id, text(node, "name", 200), bool(node, "active")));
    }
    return list;
  }

  private ConsentFixture consent(JsonNode node) {
    requireObject(node, CONSENT);
    onlyFields(node, CONSENT_FIELDS, CONSENT);
    String id = text(node, "id", 64);
    if (!ID.matcher(id).matches()) {
      throw new InvalidCatalogException("invalid consent id");
    }
    return new ConsentFixture(id, text(node, "text", 1000), bool(node, "required"));
  }

  private static void requireObject(JsonNode node, String what) {
    if (node == null || !node.isObject()) {
      throw new InvalidCatalogException(what + " must be a JSON object");
    }
  }

  private static void onlyFields(JsonNode node, Set<String> allowed, String what) {
    for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
      String f = it.next();
      if (!allowed.contains(f)) {
        throw new InvalidCatalogException("unknown field in " + what + ": " + f);
      }
    }
  }

  private static String text(JsonNode node, String field, int max) {
    JsonNode v = node.get(field);
    if (v == null || !v.isTextual() || v.asText().isBlank()) {
      throw new InvalidCatalogException("missing or empty " + field);
    }
    String s = v.asText().strip();
    if (s.codePointCount(0, s.length()) > max) {
      throw new InvalidCatalogException(field + " longer than " + max);
    }
    return s;
  }

  private static boolean bool(JsonNode node, String field) {
    JsonNode v = node.get(field);
    if (v == null || !v.isBoolean()) {
      throw new InvalidCatalogException(field + " must be true or false");
    }
    return v.asBoolean();
  }
}
