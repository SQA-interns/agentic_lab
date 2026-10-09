package si.konferenca.registration.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.SelectedOption;

/**
 * Accepts a registration (specification section 3): validation, anti-automation, duplicate check,
 * then the database row and the JSON copy in one transaction (AR-05, BR-07).
 */
public class RegistrationService {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

  private final RegistrationValidator validator;
  private final CaptchaVerifier captchaVerifier;
  private final RegistrationRepository repository;
  private final RegistrationCopyStore copyStore;
  private final RegistrationNotifier notifier;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public RegistrationService(
      RegistrationValidator validator,
      CaptchaVerifier captchaVerifier,
      RegistrationRepository repository,
      RegistrationCopyStore copyStore,
      RegistrationNotifier notifier,
      TransactionTemplate transactions,
      Clock clock) {
    this.validator = validator;
    this.captchaVerifier = captchaVerifier;
    this.repository = repository;
    this.copyStore = copyStore;
    this.notifier = notifier;
    this.transactions = transactions;
    this.clock = clock;
  }

  /**
   * Stores a valid registration; returns only once the row and the copy are both written.
   *
   * @throws ValidationException when fields are invalid
   * @throws CaptchaVerifier.CaptchaFailedException when the token is rejected
   * @throws CaptchaVerifier.CaptchaUnavailableException when verification is unavailable
   * @throws DuplicateEmailException when the email is already registered
   */
  public Registration register(RegistrationCommand command, String clientIp) {
    RegistrationValidator.ValidRegistration valid = validator.validate(command);
    captchaVerifier.verify(command.captchaToken(), clientIp);
    if (repository.existsByEmailNormalized(
        Registration.normalizeEmail(valid.participant().email()))) {
      throw new DuplicateEmailException();
    }
    Registration registration = toRegistration(valid);
    byte[] copy = store(registration);
    LOG.info("Registration {} accepted", registration.id());
    sendQuietly("participant", registration, () -> notifier.notifyParticipant(registration));
    sendQuietly("organizer", registration, () -> notifier.notifyOrganizers(registration, copy));
    return registration;
  }

  /** An email failure never undoes an accepted registration (D-11); only the id is logged. */
  private static void sendQuietly(String kind, Registration registration, Runnable send) {
    try {
      send.run();
    } catch (RuntimeException e) {
      LOG.warn(
          "The {} email for registration {} was not sent ({})",
          kind,
          registration.id(),
          e.getClass().getName());
    }
  }

  private Registration toRegistration(RegistrationValidator.ValidRegistration valid) {
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    return new Registration(
        UUID.randomUUID(),
        valid.type(),
        valid.participant(),
        now,
        valid.options().stream()
            .map(option -> new SelectedOption(option.id(), option.name(), option.category()))
            .toList(),
        valid.consents().stream()
            .map(consent -> new GivenConsent(consent.id(), consent.text(), now))
            .toList());
  }

  /** Row and copy in one transaction; a copy whose transaction did not commit is removed. */
  private byte[] store(Registration registration) {
    AtomicBoolean copyWritten = new AtomicBoolean();
    boolean committed = false;
    try {
      byte[] copy =
          transactions.execute(
              status -> {
                repository.insert(registration);
                byte[] written = copyStore.write(registration);
                copyWritten.set(true);
                return written;
              });
      committed = true;
      return copy;
    } finally {
      if (!committed && copyWritten.get()) {
        copyStore.delete(registration.id());
      }
    }
  }
}
