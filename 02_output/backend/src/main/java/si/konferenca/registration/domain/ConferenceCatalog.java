package si.konferenca.registration.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The configured options and consents (US-003, AR-04), in configuration order. */
public record ConferenceCatalog(List<ConferenceOption> options, List<ConsentDefinition> consents) {

  public ConferenceCatalog {
    options = List.copyOf(options);
    consents = List.copyOf(consents);
    requireUniqueIds(options.stream().map(ConferenceOption::id).toList(), "option");
    requireUniqueIds(consents.stream().map(ConsentDefinition::id).toList(), "consent");
  }

  private static void requireUniqueIds(List<String> ids, String kind) {
    Set<String> seen = new HashSet<>();
    for (String id : ids) {
      if (!seen.add(id)) {
        throw new IllegalArgumentException("duplicate " + kind + " id " + id);
      }
    }
  }

  public Optional<ConferenceOption> option(String id) {
    return options.stream().filter(o -> o.id().equals(id)).findFirst();
  }

  public Optional<ConsentDefinition> consent(String id) {
    return consents.stream().filter(c -> c.id().equals(id)).findFirst();
  }

  public List<ConferenceOption> activeOptions() {
    return options.stream().filter(ConferenceOption::active).toList();
  }
}
