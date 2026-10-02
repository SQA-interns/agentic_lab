package si.konferenca.registration.application;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import si.konferenca.registration.domain.CaptchaVerifier;
import si.konferenca.registration.domain.FieldError;
import si.konferenca.registration.domain.JsonCopyStore;
import si.konferenca.registration.domain.MailNotifier;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.Registration.Consent;
import si.konferenca.registration.domain.RegistrationInput;
import si.konferenca.registration.domain.RegistrationStore;
import si.konferenca.registration.domain.RegistrationValidator;
import si.konferenca.registration.domain.StorageFailedException;
import si.konferenca.registration.domain.UnitOfWork;
import si.konferenca.registration.domain.ValidationFailedException;

/**
 * Use case: accept a registration. The steps and their order are those of the specification
 * (section 4.1): validate, verify the anti-automation token, store, then answer.
 */
public class SubmitRegistration {

  private static final Logger LOG = System.getLogger(SubmitRegistration.class.getName());

  /** The consent every registration must give (D-09). */
  public record ConsentTerms(String id, String text) {}

  private final RegistrationValidator validator;
  private final CaptchaVerifier captchaVerifier;
  private final UnitOfWork unitOfWork;
  private final RegistrationStore store;
  private final JsonCopyStore jsonCopies;
  private final MailNotifier mailNotifier;
  private final ConsentTerms consentTerms;
  private final Clock clock;

  public SubmitRegistration(
      RegistrationValidator validator,
      CaptchaVerifier captchaVerifier,
      UnitOfWork unitOfWork,
      RegistrationStore store,
      JsonCopyStore jsonCopies,
      MailNotifier mailNotifier,
      ConsentTerms consentTerms,
      Clock clock) {
    this.validator = validator;
    this.captchaVerifier = captchaVerifier;
    this.unitOfWork = unitOfWork;
    this.store = store;
    this.jsonCopies = jsonCopies;
    this.mailNotifier = mailNotifier;
    this.consentTerms = consentTerms;
    this.clock = clock;
  }

  /**
   * Accepts the registration or rejects it without any side effect.
   *
   * @throws ValidationFailedException when a rule is broken or the anti-automation check failed
   * @throws StorageFailedException when the registration could not be stored
   */
  public Registration submit(RegistrationInput input) {
    RegistrationValidator.Validated validated = validator.validate(input);
    // Verified only for otherwise valid input, so invalid input costs no verification call.
    if (!captchaVerifier.verify(input.captchaToken())) {
      throw new ValidationFailedException(
          List.of(new FieldError(FieldError.FIELD_CAPTCHA_TOKEN, FieldError.CAPTCHA_FAILED)));
    }
    // Milliseconds, so the answer, the database row and the JSON copy carry the same instant.
    Instant acceptedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
    Registration registration =
        new Registration(
            UUID.randomUUID(),
            input.type(),
            acceptedAt,
            validated.values(),
            validated.options(),
            new Consent(consentTerms.id(), consentTerms.text(), acceptedAt));
    store(registration);
    notify(registration);
    return registration;
  }

  /**
   * Emails come after storage and never undo it (D-10). A failure is logged with the registration
   * id and the kind of failure only, never with participant data (ES-07).
   */
  private void notify(Registration registration) {
    try {
      mailNotifier.sendParticipantConfirmation(registration);
    } catch (RuntimeException e) {
      LOG.log(
          Level.WARNING,
          "participant confirmation of registration {0} not sent: {1}",
          registration.id(),
          e.getClass().getName());
    }
    try {
      mailNotifier.sendOrganizerNotification(registration, jsonCopies.read(registration.id()));
    } catch (RuntimeException e) {
      LOG.log(
          Level.WARNING,
          "organizer notification of registration {0} not sent: {1}",
          registration.id(),
          e.getClass().getName());
    }
  }

  /**
   * Database row and JSON copy together (AR-05): the row is written first, the copy inside the same
   * transaction, and the commit comes last. Whatever fails, neither remains.
   */
  private void store(Registration registration) {
    try {
      unitOfWork.run(
          () -> {
            store.insert(registration);
            jsonCopies.write(registration);
          });
    } catch (RuntimeException e) {
      jsonCopies.delete(registration.id());
      throw new StorageFailedException("registration " + registration.id() + " not stored", e);
    }
  }
}
