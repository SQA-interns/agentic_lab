package si.konferenca.registration.adapter.out.options;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.OptionsCatalogue;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The options catalogue read once from the configured JSON file (conference-options.schema.json).
 * Loading fails when the file is missing or does not follow the contract, so the backend does not
 * start with a broken configuration (AR-04).
 */
public final class FileOptionsCatalogue implements OptionsCatalogue {

  private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
  private static final Set<String> OPTION_FIELDS = Set.of("id", "name", "category", "active");
  private static final int MAX_OPTIONS = 200;
  private static final int MAX_NAME_LENGTH = 200;

  private final List<ConferenceOption> active;

  private FileOptionsCatalogue(List<ConferenceOption> active) {
    this.active = List.copyOf(active);
  }

  /** Reads and validates the options file. */
  public static FileOptionsCatalogue load(Path file) {
    JsonNode root;
    try {
      root = JsonMapper.builder().build().readTree(Files.readAllBytes(file));
    } catch (IOException | JacksonException e) {
      throw new IllegalStateException("The options file cannot be read: " + file, e);
    }
    JsonNode options = root.path("options");
    if (!root.isObject()
        || root.size() != 1
        || !options.isArray()
        || options.size() > MAX_OPTIONS) {
      throw invalid(file, "it must be an object with one array \"options\"");
    }
    List<ConferenceOption> active = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (JsonNode node : options) {
      ConferenceOption option = parse(file, node);
      if (!ids.add(option.id())) {
        throw invalid(file, "the option id \"" + option.id() + "\" is used twice");
      }
      if (option.active()) {
        active.add(option);
      }
    }
    return new FileOptionsCatalogue(active);
  }

  private static ConferenceOption parse(Path file, JsonNode node) {
    if (!node.isObject() || !OPTION_FIELDS.equals(Set.copyOf(node.propertyNames()))) {
      throw invalid(file, "every option has exactly id, name, category and active");
    }
    JsonNode id = node.path("id");
    JsonNode name = node.path("name");
    JsonNode category = node.path("category");
    JsonNode activeFlag = node.path("active");
    if (!id.isString() || !ID.matcher(id.asString()).matches()) {
      throw invalid(file, "an option id is not valid");
    }
    if (!name.isString()
        || name.asString().isBlank()
        || name.asString().length() > MAX_NAME_LENGTH) {
      throw invalid(file, "the name of option \"" + id.asString() + "\" is not valid");
    }
    Optional<OptionCategory> parsedCategory =
        category.isString() ? OptionCategory.fromCode(category.asString()) : Optional.empty();
    if (parsedCategory.isEmpty() || !activeFlag.isBoolean()) {
      throw invalid(
          file, "the category or active flag of option \"" + id.asString() + "\" is not valid");
    }
    return new ConferenceOption(
        id.asString(), name.asString(), parsedCategory.get(), activeFlag.asBoolean());
  }

  private static IllegalStateException invalid(Path file, String reason) {
    return new IllegalStateException("The options file " + file + " is not valid: " + reason);
  }

  @Override
  public List<ConferenceOption> activeOptions() {
    return active;
  }

  @Override
  public Optional<ConferenceOption> findActive(String id) {
    return active.stream().filter(option -> option.id().equals(id)).findFirst();
  }
}
