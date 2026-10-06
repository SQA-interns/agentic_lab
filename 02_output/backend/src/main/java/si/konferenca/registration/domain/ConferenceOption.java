package si.konferenca.registration.domain;

import java.util.Objects;
import java.util.Set;

/** A configured option (US-003): stable id, display name, category, state and audience (D-05). */
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
    if (offeredTo.isEmpty()) {
      throw new IllegalArgumentException("option " + id + " is offered to nobody");
    }
  }

  public boolean isOfferedTo(RegistrationType type) {
    return offeredTo.contains(type);
  }
}
