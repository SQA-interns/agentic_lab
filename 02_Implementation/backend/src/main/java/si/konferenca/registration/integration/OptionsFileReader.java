package si.konferenca.registration.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.OptionDefinition;

/**
 * Reads and validates the organizer-edited options file
 * (docs/contracts/conference-options.schema.json).
 */
public class OptionsFileReader {

  /** Stable option identifier format, shared with request validation. */
  public static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");

  private static final int MAX_NAME_LENGTH = 200;
  private static final Set<String> ENTRY_FIELDS = Set.of("id", "category", "name", "active");

  private final Path file;
  private final ObjectMapper mapper = new ObjectMapper();

  public OptionsFileReader(Path file) {
    this.file = file;
  }

  public Path file() {
    return file;
  }

  /** Identity of the file's current version: last-modified time and size (specification §4). */
  public record FileStamp(FileTime lastModified, long size) {}

  public FileStamp stamp() throws IOException {
    return new FileStamp(Files.getLastModifiedTime(file), Files.size(file));
  }

  /** Reads the file; throws {@link InvalidOptionsFileException} on any schema violation. */
  public List<OptionDefinition> read() throws IOException {
    JsonNode root;
    try {
      root = mapper.readTree(Files.readAllBytes(file));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new InvalidOptionsFileException("options file is not valid JSON");
    }
    if (root == null || !root.isObject() || root.size() != 1 || !root.path("options").isArray()) {
      throw new InvalidOptionsFileException(
          "options file must be an object with one 'options' array");
    }
    List<OptionDefinition> result = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    int index = 0;
    for (JsonNode entry : root.path("options")) {
      OptionDefinition def = parseEntry(entry, index);
      if (!ids.add(def.id())) {
        throw new InvalidOptionsFileException("duplicate option id at index " + index);
      }
      result.add(def);
      index++;
    }
    return result;
  }

  private static OptionDefinition parseEntry(JsonNode entry, int index) {
    if (!entry.isObject()) {
      throw invalid(index, "entry must be an object");
    }
    for (Iterator<String> names = entry.fieldNames(); names.hasNext(); ) {
      if (!ENTRY_FIELDS.contains(names.next())) {
        throw invalid(index, "unknown property");
      }
    }
    JsonNode id = entry.get("id");
    JsonNode category = entry.get("category");
    JsonNode name = entry.get("name");
    JsonNode active = entry.get("active");
    if (id == null || !id.isTextual() || !ID_PATTERN.matcher(id.asText()).matches()) {
      throw invalid(index, "id missing or not matching " + ID_PATTERN.pattern());
    }
    OptionCategory parsedCategory = parseCategory(category, index);
    if (name == null || !name.isTextual()) {
      throw invalid(index, "name missing");
    }
    String trimmedName = name.asText().strip();
    if (trimmedName.isEmpty()
        || trimmedName.codePointCount(0, trimmedName.length()) > MAX_NAME_LENGTH) {
      throw invalid(index, "name must be 1-200 characters");
    }
    if (active == null || !active.isBoolean()) {
      throw invalid(index, "active must be true or false");
    }
    return new OptionDefinition(id.asText(), parsedCategory, trimmedName, active.asBoolean());
  }

  private static OptionCategory parseCategory(JsonNode category, int index) {
    if (category == null || !category.isTextual()) {
      throw invalid(index, "category missing");
    }
    try {
      return OptionCategory.valueOf(category.asText());
    } catch (IllegalArgumentException e) {
      throw invalid(index, "unknown category");
    }
  }

  private static InvalidOptionsFileException invalid(int index, String reason) {
    return new InvalidOptionsFileException("option at index " + index + ": " + reason);
  }

  /** The options file violates its schema. */
  public static class InvalidOptionsFileException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidOptionsFileException(String message) {
      super(message);
    }
  }
}
