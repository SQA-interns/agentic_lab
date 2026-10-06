package si.konferenca.registration.application;

import java.util.List;
import si.konferenca.registration.domain.Registration;

/** Port: the registration database (database.sql). */
public interface RegistrationStore {

  boolean emailExists(String normalizedEmail);

  /** Inserts and flushes the registration inside the caller's transaction. */
  void insert(Registration registration, String jsonCopyFile);

  /** All registrations in order of acceptance. */
  List<Registration> findAll();
}
