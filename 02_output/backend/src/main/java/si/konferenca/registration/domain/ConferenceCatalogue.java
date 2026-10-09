package si.konferenca.registration.domain;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The configured options, limits and consents (US-003, AR-04). */
public final class ConferenceCatalogue {

  private final Map<String, ConferenceOption> options;
  private final Map<Category, Integer> maxSelections;
  private final List<Consent> consents;

  public ConferenceCatalogue(
      List<ConferenceOption> options,
      Map<Category, Integer> maxSelections,
      List<Consent> consents) {
    Map<String, ConferenceOption> byId = new LinkedHashMap<>();
    for (ConferenceOption option : options) {
      if (byId.put(option.id(), option) != null) {
        throw new IllegalArgumentException("duplicate option id " + option.id());
      }
    }
    this.options = byId;
    this.maxSelections = new EnumMap<>(Category.class);
    for (Category category : Category.values()) {
      this.maxSelections.put(category, maxSelections.getOrDefault(category, 1));
    }
    this.consents = List.copyOf(consents);
  }

  public Optional<ConferenceOption> option(String id) {
    return Optional.ofNullable(options.get(id));
  }

  /** Options a participant of this type may select, in configuration order. */
  public List<ConferenceOption> selectableOptions(RegistrationType type, Category category) {
    return options.values().stream()
        .filter(o -> o.category() == category && o.selectableBy(type))
        .toList();
  }

  /** D-17: maximum number of selected options in a category (1 if not configured). */
  public int maxSelections(Category category) {
    return maxSelections.get(category);
  }

  public List<Consent> consents() {
    return consents;
  }
}
