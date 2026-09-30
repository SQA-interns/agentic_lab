package si.konferenca.registration.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The configured options, per-category limits and consents (AR-04). */
public record OptionsCatalog(
    List<ConferenceOption> options,
    Map<Category, Integer> categoryLimits,
    List<ConsentDefinition> consents) {

  public OptionsCatalog {
    options = List.copyOf(options);
    categoryLimits = Map.copyOf(categoryLimits);
    consents = List.copyOf(consents);
  }

  /** Active options offered to the type, in configuration order. */
  public List<ConferenceOption> offeredTo(RegistrationType type) {
    return options.stream().filter(o -> o.offeredTo(type)).toList();
  }

  public Optional<ConferenceOption> option(String id) {
    return options.stream().filter(o -> o.id().equals(id)).findFirst();
  }

  public Optional<ConsentDefinition> consent(String id) {
    return consents.stream().filter(c -> c.id().equals(id)).findFirst();
  }

  public Optional<Integer> limit(Category category) {
    return Optional.ofNullable(categoryLimits.get(category));
  }
}
