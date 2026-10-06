package si.konferenca.registration.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import si.konferenca.registration.application.RegistrationPorts.CaptchaResult;
import si.konferenca.registration.application.RegistrationPorts.CaptchaVerifier;
import si.konferenca.registration.application.RegistrationPorts.DuplicateEmailException;
import si.konferenca.registration.application.RegistrationPorts.JsonCopyStore;
import si.konferenca.registration.application.RegistrationPorts.Notifier;
import si.konferenca.registration.application.RegistrationPorts.RegistrationRepository;
import si.konferenca.registration.application.RegistrationPorts.Transactions;
import si.konferenca.registration.domain.EmailAddress;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationSubmission;
import si.konferenca.registration.domain.RegistrationValidator;

/**
 * Registers a participant (US-001, US-002, US-005) in the order of docs/02_specification.md §3:
 * validate, verify the token, check the email, store row and JSON copy together, then notify.
 */
public class RegistrationService {

  private final RegistrationValidator validator;
  private final CaptchaVerifier captcha;
  private final RegistrationRepository repository;
  private final JsonCopyStore copies;
  private final Transactions transactions;
  private final Notifier notifier;
  private final Clock clock;
  private final Supplier<UUID> ids;

  public RegistrationService(
      RegistrationValidator validator,
      CaptchaVerifier captcha,
      RegistrationRepository repository,
      JsonCopyStore copies,
      Transactions transactions,
      Notifier notifier,
      Clock clock,
      Supplier<UUID> ids) {
    this.validator = validator;
    this.captcha = captcha;
    this.repository = repository;
    this.copies = copies;
    this.transactions = transactions;
    this.notifier = notifier;
    this.clock = clock;
    this.ids = ids;
  }

  public RegistrationResult register(RegistrationSubmission submission) {
    RegistrationValidator.Result validation = validator.validate(submission);
    if (!validation.isValid()) {
      return new RegistrationResult.Invalid(validation.errors());
    }
    CaptchaResult verified = captcha.verify(submission.captchaToken());
    if (verified == CaptchaResult.UNAVAILABLE) {
      return new RegistrationResult.CaptchaUnavailable();
    }
    if (verified != CaptchaResult.PASSED) {
      return new RegistrationResult.CaptchaRejected();
    }
    RegistrationValidator.ValidRegistration valid = validation.registration().orElseThrow();
    if (repository.existsByNormalizedEmail(EmailAddress.normalize(valid.participant().email()))) {
      return new RegistrationResult.DuplicateEmail();
    }
    Registration registration = toRegistration(valid);
    java.util.Optional<byte[]> copy = store(registration);
    if (copy.isEmpty()) {
      return new RegistrationResult.DuplicateEmail();
    }
    notifier.registrationAccepted(registration, copy.get());
    return new RegistrationResult.Accepted(registration);
  }

  private Registration toRegistration(RegistrationValidator.ValidRegistration valid) {
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    return new Registration(
        ids.get(),
        valid.type(),
        valid.participant(),
        valid.options().stream()
            .map(o -> new Registration.SelectedOption(o.id(), o.name(), o.category()))
            .toList(),
        valid.consents().stream()
            .map(c -> new Registration.GivenConsent(c.id(), c.text(), now))
            .toList(),
        now);
  }

  /**
   * Database row and JSON copy, both or neither (AR-05). Returns the copy, or nothing when a
   * concurrent registration took the email.
   */
  private java.util.Optional<byte[]> store(Registration registration) {
    AtomicReference<byte[]> copy = new AtomicReference<>();
    try {
      transactions.inTransaction(
          () -> {
            repository.insert(registration);
            copy.set(copies.write(registration));
          });
      return java.util.Optional.of(copy.get());
    } catch (DuplicateEmailException e) {
      removeCopy(registration, copy);
      return java.util.Optional.empty();
    } catch (RuntimeException e) {
      removeCopy(registration, copy);
      throw new StorageFailedException(registration.id(), e);
    }
  }

  private void removeCopy(Registration registration, AtomicReference<byte[]> copy) {
    if (copy.get() != null) {
      copies.delete(registration.id());
    }
  }
}
