package si.konferenca.registration.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.application.RegistrationValidator.ValidRegistration;
import si.konferenca.registration.application.ValidationException.FieldViolation;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationRepository;
import si.konferenca.registration.domain.SelectedOption;

/**
 * The registration use case (02_specification.md 4.1): validate, verify reCAPTCHA, reject
 * duplicates, store the database row and the JSON copy together (AR-05, BR-07), then send the
 * emails.
 */
@Service
public class RegistrationService {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

  private final RegistrationValidator validator;
  private final CaptchaVerifier captcha;
  private final RegistrationRepository repository;
  private final JsonCopyStore copies;
  private final NotificationSender notifications;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public RegistrationService(
      RegistrationValidator validator,
      CaptchaVerifier captcha,
      RegistrationRepository repository,
      JsonCopyStore copies,
      NotificationSender notifications,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.validator = validator;
    this.captcha = captcha;
    this.repository = repository;
    this.copies = copies;
    this.notifications = notifications;
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  /** A stored registration and the exact bytes of its JSON copy. */
  public record Accepted(RegistrationCopy copy, byte[] json) {}

  public Accepted register(RegistrationCommand command, String clientAddress) {
    ValidRegistration valid = validator.validate(command);
    if (!captcha.verify(command.recaptchaToken(), clientAddress)) {
      throw new ValidationException(
          List.of(
              new FieldViolation(
                  "recaptchaToken", "The anti-robot check failed. Please try again.")));
    }
    if (repository.existsByEmailIgnoreCase(valid.details().email())) {
      throw new DuplicateRegistrationException();
    }
    Registration registration = newRegistration(valid);
    RegistrationCopy copy = RegistrationCopy.of(registration);
    byte[] json = store(registration, copy);
    LOG.info("Registration {} accepted", registration.getId());
    sendSafely(
        "participant", registration.getId(), () -> notifications.sendParticipantConfirmation(copy));
    sendSafely(
        "organizer",
        registration.getId(),
        () -> notifications.sendOrganizerNotification(copy, json));
    return new Accepted(copy, json);
  }

  private Registration newRegistration(ValidRegistration valid) {
    Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
    List<SelectedOption> options =
        valid.options().stream()
            .map(o -> new SelectedOption(o.id(), o.name(), o.category().code()))
            .toList();
    List<GivenConsent> consents =
        valid.consentIds().stream().map(id -> new GivenConsent(id, now)).toList();
    return new Registration(UUID.randomUUID(), now, valid.details(), options, consents);
  }

  private byte[] store(Registration registration, RegistrationCopy copy) {
    try {
      return transaction.execute(
          status -> {
            repository.saveAndFlush(registration);
            return copies.write(copy);
          });
    } catch (DataIntegrityViolationException e) {
      copies.delete(registration.getId());
      throw new DuplicateRegistrationException();
    } catch (RuntimeException e) {
      copies.delete(registration.getId());
      throw e;
    }
  }

  /** Emails never undo an accepted registration (D-08); failures are logged by id only. */
  private static void sendSafely(String kind, UUID id, Runnable send) {
    try {
      send.run();
    } catch (RuntimeException e) {
      LOG.error("Email {} failed for registration {} ({})", kind, id, e.getClass().getSimpleName());
    }
  }
}
