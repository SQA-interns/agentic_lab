package si.konferenca.registration.domain;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The configured options, consents and category maxima (US-003, AR-04). */
public final class ConferenceCatalog {

  private final Map<String, ConferenceOption> options;
  private final List<ConsentDefinition> consents;
  private final Map<Category, Integer> categoryLimits;

  public ConferenceCatalog(
      List<ConferenceOption> options,
      List<ConsentDefinition> consents,
      Map<Category, Integer> categoryLimits) {
    Map<String, ConferenceOption> byId = new LinkedHashMap<>();
    for (ConferenceOption option : options) {
      if (byId.putIfAbsent(option.id(), option) != null) {
        throw new IllegalArgumentException("duplicate option id " + option.id());
      }
    }
    this.options = byId;
    this.consents = List.copyOf(consents);
    this.categoryLimits =
        categoryLimits.isEmpty() ? new EnumMap<>(Category.class) : new EnumMap<>(categoryLimits);
  }

  public Optional<ConferenceOption> option(String id) {
    return Optional.ofNullable(options.get(id));
  }

  /** Active options in configuration order. */
  public List<ConferenceOption> activeOptions() {
    return options.values().stream().filter(ConferenceOption::active).toList();
  }

  public List<ConsentDefinition> consents() {
    return consents;
  }

  public Optional<Integer> limit(Category category) {
    return Optional.ofNullable(categoryLimits.get(category));
  }
}
