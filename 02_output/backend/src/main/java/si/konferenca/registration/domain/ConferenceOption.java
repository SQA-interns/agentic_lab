package si.konferenca.registration.domain;

import java.util.Set;

/** A configured workshop, event, meal or other activity (`conference-config.schema.json`). */
public record ConferenceOption(
    String id,
    String displayName,
    Category category,
    boolean active,
    Set<RegistrationType> availableTo) {

  public ConferenceOption {
    availableTo = Set.copyOf(availableTo);
  }

  public boolean availableTo(RegistrationType type) {
    return availableTo.contains(type);
  }
}
