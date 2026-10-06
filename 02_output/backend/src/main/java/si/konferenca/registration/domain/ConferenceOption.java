package si.konferenca.registration.domain;

import java.util.Objects;
import java.util.Set;

/** A configured workshop, event, meal or other activity (US-003). */
public record ConferenceOption(
    String id,
    String name,
    OptionCategory category,
    boolean active,
    Set<RegistrationType> offeredTo) {

  public ConferenceOption {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(category, "category");
    offeredTo = Set.copyOf(offeredTo);
  }

  public boolean isOfferedTo(RegistrationType type) {
    return offeredTo.contains(type);
  }
}
