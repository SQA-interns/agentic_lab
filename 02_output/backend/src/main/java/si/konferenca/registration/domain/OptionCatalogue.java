package si.konferenca.registration.domain;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The configured options, per-category limits and consents (AR-04). */
public final class OptionCatalogue {

  /** Limit for a category the configuration does not mention (D-14). */
  public static final int DEFAULT_LIMIT = 1;

  private final Map<Category, Integer> limits;
  private final Map<String, ConferenceOption> options;
  private final List<Consent> consents;

  public OptionCatalogue(
      Map<Category, Integer> limits, List<ConferenceOption> options, List<Consent> consents) {
    this.limits = new EnumMap<>(Category.class);
    for (Category category : Category.values()) {
      this.limits.put(category, limits.getOrDefault(category, DEFAULT_LIMIT));
    }
    this.options = new LinkedHashMap<>();
    for (ConferenceOption option : options) {
      if (this.options.put(option.id(), option) != null) {
        throw new IllegalArgumentException("duplicate option id " + option.id());
      }
    }
    if (consents.stream().map(Consent::id).distinct().count() != consents.size()) {
      throw new IllegalArgumentException("duplicate consent id");
    }
    if (consents.stream().noneMatch(Consent::mandatory)) {
      throw new IllegalArgumentException("at least one consent must be mandatory");
    }
    this.consents = List.copyOf(consents);
  }

  public int limit(Category category) {
    return limits.get(category);
  }

  public Optional<ConferenceOption> option(String id) {
    return Optional.ofNullable(options.get(id));
  }

  public List<ConferenceOption> activeOptions() {
    return options.values().stream().filter(ConferenceOption::active).toList();
  }

  public List<Consent> consents() {
    return consents;
  }

  public Optional<Consent> consent(String id) {
    return consents.stream().filter(c -> c.id().equals(id)).findFirst();
  }
}
