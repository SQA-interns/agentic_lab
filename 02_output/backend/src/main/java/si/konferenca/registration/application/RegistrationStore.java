package si.konferenca.registration.application;

import java.util.List;
import si.konferenca.registration.domain.Registration;

/** Port: the database of registrations (BR-07). */
public interface RegistrationStore {

  boolean existsByNormalizedEmail(String normalizedEmail);

  /**
   * Inserts the registration and flushes, inside the caller's transaction.
   *
   * @throws DuplicateEmailException if another registration with the same email was stored first
   */
  void insert(Registration registration);

  /** All registrations ordered by submission time. */
  List<Registration> findAll();
}
