package si.konferenca.registration.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import si.konferenca.registration.domain.FieldError;
import si.konferenca.registration.domain.OptionsCatalog;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationSubmission;
import si.konferenca.registration.domain.RegistrationValidator;
import si.konferenca.registration.domain.ValidationResult;

/** Accepts a registration: validate, store in the database and as a JSON copy (AR-05). */
@Service
public class RegistrationService {

  private final RegistrationValidator validator;
  private final CaptchaVerifier captcha;
  private final RegistrationStore store;
  private final JsonCopyStore copies;
  private final Clock clock;

  public RegistrationService(
      OptionsCatalog catalog,
      CaptchaVerifier captcha,
      RegistrationStore store,
      JsonCopyStore copies,
      Clock clock) {
    this.validator = new RegistrationValidator(catalog);
    this.captcha = captcha;
    this.store = store;
    this.copies = copies;
    this.clock = clock;
  }

  /**
   * Validates and stores the submission. Returns only after the database row and the JSON copy are
   * both written and the transaction commits; otherwise nothing remains.
   *
   * @throws RegistrationRejectedException when a field or the captcha token is invalid
   * @throws StorageException when the JSON copy cannot be written
   */
  @Transactional
  public Registration register(RegistrationSubmission submission) {
    ValidationResult result = validator.validate(submission);
    if (!result.valid()) {
      throw new RegistrationRejectedException(result.errors());
    }
    // Verified last, so an invalid form does not spend the single-use token.
    if (!captcha.verify(submission.captchaToken())) {
      throw new RegistrationRejectedException(
          List.of(new FieldError("captchaToken", "The robot check failed. Please try it again.")));
    }
    Registration registration =
        store.insert(
            Registration.accept(
                UUID.randomUUID(),
                result.participant(),
                result.options(),
                result.consents(),
                clock.instant()));
    UUID reference = registration.reference();
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              copies.delete(reference);
            }
          }
        });
    copies.write(registration);
    return registration;
  }
}
