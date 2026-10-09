package si.konferenca.registration.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import si.konferenca.registration.domain.Registration;

/**
 * Stores a registration atomically (AR-05): database row and JSON copy in one transaction; the
 * accepted event is delivered to listeners only after the commit.
 */
@Service
public class RegistrationTransaction {

  private final RegistrationStore store;
  private final JsonCopyStore jsonCopies;
  private final ApplicationEventPublisher events;

  public RegistrationTransaction(
      RegistrationStore store, JsonCopyStore jsonCopies, ApplicationEventPublisher events) {
    this.store = store;
    this.jsonCopies = jsonCopies;
    this.events = events;
  }

  @Transactional
  public void store(Registration registration) {
    store.insert(registration);
    byte[] copy = jsonCopies.write(registration);
    events.publishEvent(new RegistrationAccepted(registration, copy));
  }
}
