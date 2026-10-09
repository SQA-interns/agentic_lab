package si.konferenca.registration.domain;

import java.util.Set;

/** A configured workshop, event, meal or other activity (BR-04, D-14). */
public record ConferenceOption(
    String id, String name, Category category, boolean active, Set<RegistrationType> availableTo) {

  public ConferenceOption {
    availableTo = Set.copyOf(availableTo);
  }

  /** True if a participant of this type may select the option now. */
  public boolean selectableBy(RegistrationType type) {
    return active && availableTo.contains(type);
  }
}
