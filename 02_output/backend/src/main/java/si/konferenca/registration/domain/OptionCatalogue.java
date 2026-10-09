package si.konferenca.registration.domain;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The conference options, category limits and consents from configuration (AR-04). */
public final class OptionCatalogue {

  private final Map<Category, Integer> maxSelections;
  private final Map<String, ConferenceOption> options;
  private final List<ConsentDefinition> consents;

  public OptionCatalogue(
      Map<Category, Integer> maxSelections,
      List<ConferenceOption> options,
      List<ConsentDefinition> consents) {
    this.maxSelections = new EnumMap<>(Category.class);
    for (Category category : Category.values()) {
      this.maxSelections.put(category, maxSelections.getOrDefault(category, 1));
    }
    this.options = new LinkedHashMap<>();
    for (ConferenceOption option : options) {
      this.options.put(option.id(), option);
    }
    this.consents = List.copyOf(consents);
  }

  public int maxSelections(Category category) {
    return maxSelections.get(category);
  }

  public Optional<ConferenceOption> option(String id) {
    return Optional.ofNullable(options.get(id));
  }

  /** Active options of a category, in configuration order. */
  public List<ConferenceOption> activeOptions(Category category) {
    return options.values().stream()
        .filter(option -> option.active() && option.category() == category)
        .toList();
  }

  public List<ConsentDefinition> consents() {
    return consents;
  }

  public Optional<ConsentDefinition> consent(String id) {
    return consents.stream().filter(consent -> consent.id().equals(id)).findFirst();
  }
}
