package si.konferenca.registration.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import si.konferenca.registration.domain.EmailAddress;
import si.konferenca.registration.domain.Registration;

/** Database storage of registrations (BR-07). Callers own the transaction. */
@Repository
public class RegistrationStore {

  private final RegistrationRepository repository;

  RegistrationStore(RegistrationRepository repository) {
    this.repository = repository;
  }

  public boolean emailRegistered(String email) {
    return repository.existsByEmailNormalized(EmailAddress.normalise(email));
  }

  /** Inserts and flushes, so constraint violations surface inside the caller's transaction. */
  public void insert(Registration registration) {
    repository.saveAndFlush(RegistrationEntity.from(registration));
  }

  public List<Registration> findAllOrderedByReceivedAt() {
    return repository.findAllByOrderByReceivedAtAsc().stream()
        .map(RegistrationEntity::toDomain)
        .toList();
  }

  public List<UUID> idsReceivedBefore(Instant cutoff) {
    return repository.findIdsReceivedBefore(cutoff);
  }

  public void delete(UUID id) {
    repository.deleteById(id);
  }
}
