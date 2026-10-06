package si.konferenca.registration.application;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.Registration;

/**
 * Accepts a registration: validate, verify anti-automation, store in the database and as a JSON
 * copy in one unit, then hand the emails over (spec section 3, AR-05, D-14).
 */
public class RegistrationService {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

  private final RegistrationValidator validator;
  private final CaptchaVerifier captcha;
  private final RegistrationStore store;
  private final JsonCopyStore copies;
  private final Notifier notifier;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public RegistrationService(
      RegistrationValidator validator,
      CaptchaVerifier captcha,
      RegistrationStore store,
      JsonCopyStore copies,
      Notifier notifier,
      TransactionTemplate transaction,
      Clock clock) {
    this.validator = validator;
    this.captcha = captcha;
    this.store = store;
    this.copies = copies;
    this.notifier = notifier;
    this.transaction = transaction;
    this.clock = clock;
  }

  /**
   * Registers the participant; returns only after the database row and the JSON copy exist.
   *
   * @throws ValidationException field errors or failed anti-automation check
   * @throws DuplicateEmailException the email is already registered
   * @throws ServiceUnavailableException storage or verification unavailable
   */
  public Registration register(RegistrationCommand command, String clientAddress) {
    Registration registration =
        validator.validate(
            command, UUID.randomUUID(), clock.instant().truncatedTo(ChronoUnit.MILLIS));
    if (!captcha.verify(command.recaptchaToken(), clientAddress)) {
      throw new ValidationException(
          List.of(new FieldError("recaptchaToken", FieldError.RECAPTCHA_FAILED)));
    }
    byte[] json = copies.serialize(registration);
    boolean[] copyWritten = {false};
    try {
      transaction.executeWithoutResult(
          status -> {
            if (store.emailExists(registration.normalizedEmail())) {
              throw new DuplicateEmailException();
            }
            store.insert(registration, copies.fileNameFor(registration));
            copies.write(registration, json);
            copyWritten[0] = true;
          });
    } catch (DuplicateEmailException e) {
      throw e;
    } catch (RuntimeException e) {
      if (copyWritten[0]) {
        copies.delete(registration);
      }
      if (e instanceof DataIntegrityViolationException && isDuplicate(registration)) {
        throw new DuplicateEmailException();
      }
      LOG.error("Registration {} not stored: {}", registration.id(), e.getClass().getSimpleName());
      throw new ServiceUnavailableException("Registration could not be stored", e);
    }
    LOG.info("Registration {} accepted", registration.id());
    notifier.registrationAccepted(registration, json);
    return registration;
  }

  /** A concurrent registration with the same email won the unique constraint. */
  private boolean isDuplicate(Registration registration) {
    try {
      return Boolean.TRUE.equals(
          transaction.execute(s -> store.emailExists(registration.normalizedEmail())));
    } catch (RuntimeException e) {
      return false;
    }
  }
}
