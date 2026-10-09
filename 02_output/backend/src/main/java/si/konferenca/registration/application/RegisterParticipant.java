package si.konferenca.registration.application;

import java.util.List;
import org.springframework.stereotype.Service;
import si.konferenca.registration.domain.ErrorCode;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationValidator;
import si.konferenca.registration.domain.Submission;
import si.konferenca.registration.domain.ValidationError;

/** Use case: register a participant (US-001, US-002, US-005; flow in specification section 4). */
@Service
public class RegisterParticipant {

  private final RegistrationValidator validator;
  private final CaptchaVerifier captcha;
  private final RegistrationStore store;
  private final RegistrationTransaction transaction;

  public RegisterParticipant(
      RegistrationValidator validator,
      CaptchaVerifier captcha,
      RegistrationStore store,
      RegistrationTransaction transaction) {
    this.validator = validator;
    this.captcha = captcha;
    this.store = store;
    this.transaction = transaction;
  }

  /**
   * Validates, verifies the anti-automation token, checks for a duplicate and stores.
   *
   * @throws StorageException if the registration could not be stored
   */
  public RegistrationResult register(
      Submission submission, String captchaToken, String remoteAddress) {
    RegistrationValidator.Outcome outcome = validator.validate(submission);
    if (!outcome.valid()) {
      return new RegistrationResult.Rejected(outcome.errors(), false);
    }
    if (captchaToken == null
        || captchaToken.isBlank()
        || !captcha.verify(captchaToken, remoteAddress)) {
      return new RegistrationResult.Rejected(
          List.of(ValidationError.of("recaptchaToken", ErrorCode.CAPTCHA_FAILED)), false);
    }
    Registration registration = outcome.registration();
    if (store.existsByNormalizedEmail(registration.normalizedEmail())) {
      return duplicate();
    }
    try {
      transaction.store(registration);
    } catch (DuplicateEmailException e) {
      return duplicate();
    }
    return new RegistrationResult.Accepted(registration);
  }

  private static RegistrationResult duplicate() {
    return new RegistrationResult.Rejected(
        List.of(ValidationError.of("email", ErrorCode.ALREADY_REGISTERED)), true);
  }
}
