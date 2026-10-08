package si.konferenca.registration.domain;

import java.util.Set;

/** A configured option (BR-04, D-11). */
public record ConferenceOption(
    String id,
    String name,
    Category category,
    boolean active,
    Set<RegistrationType> registrationTypes) {

  public ConferenceOption {
    registrationTypes = Set.copyOf(registrationTypes);
  }

  public boolean availableTo(RegistrationType type) {
    return registrationTypes.contains(type);
  }
}
