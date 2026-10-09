package si.konferenca.registration.application;

import java.util.List;
import si.konferenca.registration.domain.Registration;

/** Database storage of registrations (port; BR-07). */
public interface RegistrationRepository {

  boolean existsByEmailNormalized(String emailNormalized);

  /**
   * Inserts and flushes within the current transaction.
   *
   * @throws DuplicateEmailException when another registration already uses the address
   */
  void insert(Registration registration);

  /** Every registration with its options and consents, oldest first. */
  List<Registration> findAllOldestFirst();
}
