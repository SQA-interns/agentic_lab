package si.konferenca.registration.application;

import static java.time.temporal.ChronoUnit.MILLIS;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationRepository;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;

/** The registration use case (specification section 3). */
public class RegistrationService {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);
  private static final String EMAIL_CONSTRAINT = "registration_email_normalized_uk";

  private final RegistrationValidator validator = new RegistrationValidator();
  private final RegistrationRepository repository;
  private final JsonCopyStore copies;
  private final CaptchaVerifier captcha;
  private final OptionsCatalog catalog;
  private final RegistrationJson json;
  private final RegistrationNotifier notifier;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public RegistrationService(
      RegistrationRepository repository,
      JsonCopyStore copies,
      CaptchaVerifier captcha,
      OptionsCatalog catalog,
      RegistrationJson json,
      RegistrationNotifier notifier,
      TransactionTemplate transaction,
      Clock clock) {
    this.repository = repository;
    this.copies = copies;
    this.captcha = captcha;
    this.catalog = catalog;
    this.json = json;
    this.notifier = notifier;
    this.transaction = transaction;
    this.clock = clock;
  }

  /** The accepted registration and its raw JSON copy. */
  public record Accepted(UUID id, RegistrationType type, Instant acceptedAt) {}

  /**
   * Validates, checks the anti-automation token, then stores the registration in the database and
   * as a JSON copy; returns only when both are written.
   */
  public Accepted register(RegistrationCommand command, String clientAddress) {
    ConferenceOptions options = catalog.options();
    RegistrationValidator.ValidRegistration valid = validator.validate(command, options);
    if (!captcha.verify(valid.captchaToken(), clientAddress)) {
      throw new RegistrationRejectedException(
          List.of(new FieldError("captchaToken", "CAPTCHA_FAILED")));
    }
    if (repository.existsByEmailNormalized(
        Registration.normalizeEmail(valid.participant().email()))) {
      throw new EmailAlreadyRegisteredException();
    }
    Registration registration =
        new Registration(
            UUID.randomUUID(),
            valid.type(),
            valid.participant(),
            valid.options().stream()
                .map(o -> new SelectedOption(o.id(), o.name(), o.category()))
                .toList(),
            options.consent().id(),
            options.consent().text(),
            clock.instant().truncatedTo(MILLIS));
    byte[] copy = json.toJson(registration);
    store(registration, copy);
    LOG.info("registration {} accepted", registration.id());
    notifyQuietly(registration, copy);
    return new Accepted(registration.id(), registration.type(), registration.acceptedAt());
  }

  /** Emails come after storage; their failure never changes the outcome (D-08). */
  private void notifyQuietly(Registration registration, byte[] copy) {
    try {
      notifier.registrationAccepted(registration, copy);
    } catch (RuntimeException e) {
      LOG.warn(
          "notifications for registration {} failed: {}",
          registration.id(),
          e.getClass().getSimpleName());
    }
  }

  private void store(Registration registration, byte[] copy) {
    AtomicReference<Path> written = new AtomicReference<>();
    try {
      transaction.executeWithoutResult(
          status -> {
            repository.saveAndFlush(registration);
            written.set(copies.write(registration.id(), registration.acceptedAt(), copy));
          });
    } catch (DataIntegrityViolationException e) {
      discard(written.get());
      if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(EMAIL_CONSTRAINT)) {
        throw new EmailAlreadyRegisteredException();
      }
      throw unavailable(registration, e);
    } catch (RuntimeException e) {
      discard(written.get());
      throw unavailable(registration, e);
    }
  }

  private void discard(Path copy) {
    if (copy != null) {
      copies.delete(copy);
    }
  }

  private static StorageUnavailableException unavailable(Registration registration, Exception e) {
    LOG.warn("registration {} not stored: {}", registration.id(), e.getClass().getSimpleName());
    return new StorageUnavailableException(e);
  }
}
