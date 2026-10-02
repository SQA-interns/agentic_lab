package si.konferenca.registration.domain;

import java.util.List;

/** Port: the database of accepted registrations (BR-07). */
public interface RegistrationStore {

  /** Writes the registration to the database at once, inside the current unit of work. */
  void insert(Registration registration);

  /** Every stored registration, oldest first. */
  List<Registration> findAll();
}
