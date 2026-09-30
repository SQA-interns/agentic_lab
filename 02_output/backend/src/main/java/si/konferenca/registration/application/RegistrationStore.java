package si.konferenca.registration.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import si.konferenca.registration.domain.Registration;

/** Port: the registration database (specification section 6). */
public interface RegistrationStore {

  /** Inserts the registration and flushes, so database errors surface before the JSON copy. */
  Registration insert(Registration registration);

  Optional<Registration> findByReference(UUID reference);

  /** Every registration, oldest first (export order). */
  List<Registration> findAllOldestFirst();

  /** Registrations with a failed email, or with an email still pending since before the time. */
  List<Registration> findNeedingMail(Instant pendingBefore);

  /** Persists the mail status fields of the registration. */
  void saveMailStatus(Registration registration);
}
