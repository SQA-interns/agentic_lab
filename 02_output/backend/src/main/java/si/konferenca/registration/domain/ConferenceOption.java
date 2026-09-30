package si.konferenca.registration.domain;

import java.util.Set;

/** A configured workshop, event, meal or other activity (US-003, D-09). */
public record ConferenceOption(
    String id, String name, Category category, boolean active, Set<RegistrationType> types) {

  public ConferenceOption {
    types = Set.copyOf(types);
  }

  public boolean offeredTo(RegistrationType type) {
    return active && types.contains(type);
  }
}
