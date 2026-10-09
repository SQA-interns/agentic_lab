package si.konferenca.registration.domain;

import java.util.Set;

/** A configured option (US-003). */
public record ConferenceOption(
    String id, String name, Category category, boolean active, Set<RegistrationType> availableTo) {

  public ConferenceOption {
    availableTo = Set.copyOf(availableTo);
  }

  public boolean isAvailableTo(RegistrationType type) {
    return availableTo.contains(type);
  }
}
