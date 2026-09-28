package si.konferenca.registration.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.service.ConferenceOptionCatalog;

/**
 * Loads the conference options from a JSON file at startup ({@code APP_OPTIONS_FILE}, or the
 * bundled {@code conference-options.json}). Invalid entries are skipped; duplicate ids fail
 * startup.
 */
@Component
public class FileConferenceOptionCatalog implements ConferenceOptionCatalog {

  static final String DEFAULT_RESOURCE = "conference-options.json";

  private static final Logger LOG = LoggerFactory.getLogger(FileConferenceOptionCatalog.class);
  private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
  private static final int MAX_NAME_LENGTH = 200;

  private final Map<String, ConferenceOption> optionsById;

  public FileConferenceOptionCatalog(AppProperties properties, ObjectMapper objectMapper) {
    this.optionsById = load(properties.options().file(), objectMapper);
    LOG.info("Loaded {} conference options", optionsById.size());
  }

  @Override
  public List<ConferenceOption> findAll() {
    return List.copyOf(optionsById.values());
  }

  @Override
  public Optional<ConferenceOption> findById(String id) {
    return Optional.ofNullable(optionsById.get(id));
  }

  private static Map<String, ConferenceOption> load(String file, ObjectMapper objectMapper) {
    try (InputStream in = open(file)) {
      return parse(objectMapper.readTree(in));
    } catch (IOException e) {
      throw new UncheckedIOException("Conference option configuration cannot be read", e);
    }
  }

  private static InputStream open(String file) throws IOException {
    if (file == null || file.isBlank()) {
      return new ClassPathResource(DEFAULT_RESOURCE).getInputStream();
    }
    return Files.newInputStream(Path.of(file));
  }

  static Map<String, ConferenceOption> parse(JsonNode root) {
    JsonNode entries = root == null ? null : root.get("options");
    if (entries == null || !entries.isArray()) {
      throw new IllegalStateException("Conference option configuration must contain 'options'");
    }
    Map<String, ConferenceOption> result = new LinkedHashMap<>();
    List<Integer> skipped = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      Optional<ConferenceOption> option = toOption(entries.get(i));
      if (option.isEmpty()) {
        skipped.add(i);
        continue;
      }
      if (result.putIfAbsent(option.get().id(), option.get()) != null) {
        throw new IllegalStateException(
            "Duplicate conference option id in configuration: " + option.get().id());
      }
    }
    if (!skipped.isEmpty()) {
      LOG.warn("Skipped invalid conference option entries at indexes {}", skipped);
    }
    return Collections.unmodifiableMap(result);
  }

  private static Optional<ConferenceOption> toOption(JsonNode node) {
    if (node == null || !node.isObject()) {
      return Optional.empty();
    }
    JsonNode id = node.get("id");
    JsonNode name = node.get("name");
    JsonNode category = node.get("category");
    JsonNode active = node.get("active");
    if (!isText(id) || !isText(name) || !isText(category) || active == null) {
      return Optional.empty();
    }
    if (!active.isBoolean() || !ID_PATTERN.matcher(id.asText()).matches()) {
      return Optional.empty();
    }
    String displayName = name.asText().strip();
    if (displayName.isEmpty() || displayName.length() > MAX_NAME_LENGTH) {
      return Optional.empty();
    }
    return parseCategory(category.asText())
        .map(c -> new ConferenceOption(id.asText(), displayName, c, active.asBoolean()));
  }

  private static boolean isText(JsonNode node) {
    return node != null && node.isTextual();
  }

  private static Optional<OptionCategory> parseCategory(String value) {
    try {
      return Optional.of(OptionCategory.valueOf(value.strip().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
