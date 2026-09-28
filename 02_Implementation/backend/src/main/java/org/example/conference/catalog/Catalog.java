package org.example.conference.catalog;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable startup snapshot of the option and consent catalog. */
public final class Catalog {

  private final String conferenceName;
  private final Map<OptionGroup, List<CatalogOption>> options;
  private final List<ConsentDefinition> consents;

  public Catalog(
      String conferenceName,
      Map<OptionGroup, List<CatalogOption>> options,
      List<ConsentDefinition> consents) {
    this.conferenceName = conferenceName;
    EnumMap<OptionGroup, List<CatalogOption>> copy = new EnumMap<>(OptionGroup.class);
    for (OptionGroup group : OptionGroup.values()) {
      copy.put(group, List.copyOf(options.getOrDefault(group, List.of())));
    }
    this.options = copy;
    this.consents = List.copyOf(consents);
  }

  public String conferenceName() {
    return conferenceName;
  }

  /** All configured options of a group in configuration order (active and inactive). */
  public List<CatalogOption> options(OptionGroup group) {
    return options.get(group);
  }

  public List<CatalogOption> activeOptions(OptionGroup group) {
    return options.get(group).stream().filter(CatalogOption::active).toList();
  }

  /** Returns the option only when it is known in the group and active. */
  public Optional<CatalogOption> findActive(OptionGroup group, String id) {
    return options.get(group).stream().filter(o -> o.active() && o.id().equals(id)).findFirst();
  }

  public List<ConsentDefinition> consents() {
    return consents;
  }

  public Optional<ConsentDefinition> findConsent(String id) {
    return consents.stream().filter(c -> c.id().equals(id)).findFirst();
  }
}
