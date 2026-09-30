package si.konferenca.registration.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import si.konferenca.registration.application.RegistrationStore;
import si.konferenca.registration.domain.MailStatus;
import si.konferenca.registration.domain.Registration;

/** PostgreSQL adapter of the registration store. */
public class JpaRegistrationStore implements RegistrationStore {

  private final JpaRegistrationRepository repository;

  public JpaRegistrationStore(JpaRegistrationRepository repository) {
    this.repository = repository;
  }

  @Override
  public Registration insert(Registration registration) {
    return repository.saveAndFlush(registration);
  }

  @Override
  public Optional<Registration> findByReference(UUID reference) {
    return repository.findByReference(reference);
  }

  @Override
  public List<Registration> findAllOldestFirst() {
    return repository.findAllByOrderBySubmittedAtAscIdAsc();
  }

  @Override
  public List<Registration> findNeedingMail(Instant pendingBefore) {
    return repository.findNeedingMail(MailStatus.FAILED, MailStatus.PENDING, pendingBefore);
  }

  @Override
  @Transactional
  public void saveMailStatus(Registration r) {
    repository.updateMailStatus(
        r.id(),
        r.participantMailStatus(),
        r.organizerMailStatus(),
        r.mailAttempts(),
        r.lastMailAttemptAt());
  }
}
