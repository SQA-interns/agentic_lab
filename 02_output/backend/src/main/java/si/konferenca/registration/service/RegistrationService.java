package si.konferenca.registration.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.ConferenceCatalog;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.SelectedOption;
import si.konferenca.registration.integration.AntiAutomationVerifier;
import si.konferenca.registration.persistence.JsonCopyStore;
import si.konferenca.registration.persistence.RegistrationRepository;

/**
 * The registration flow of `docs/02_specification.md` section 3: anti-automation, validation,
 * duplicate check, then the database row and the raw JSON copy in one transaction (BR-07, AR-05).
 */
@Service
public class RegistrationService {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);
  private static final String EMAIL_UNIQUE = "registration_email_unique";

  /** An accepted registration and the stored bytes of its raw JSON copy. */
  public record Accepted(Registration registration, byte[] jsonCopy) {

    public Accepted {
      jsonCopy = jsonCopy.clone();
    }

    @Override
    public byte[] jsonCopy() {
      return jsonCopy.clone();
    }
  }

  private final AntiAutomationVerifier antiAutomation;
  private final RegistrationValidator validator;
  private final RegistrationRepository repository;
  private final JsonCopyStore copies;
  private final TransactionTemplate transaction;

  public RegistrationService(
      AntiAutomationVerifier antiAutomation,
      ConferenceCatalog catalog,
      RegistrationRepository repository,
      JsonCopyStore copies,
      PlatformTransactionManager transactionManager) {
    this.antiAutomation = antiAutomation;
    this.validator = new RegistrationValidator(catalog);
    this.repository = repository;
    this.copies = copies;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  public Accepted register(RegistrationRequest request, String remoteAddress) {
    switch (antiAutomation.verify(request.antiAutomationToken(), remoteAddress)) {
      case FAILED -> throw new RegistrationRejectedException(ErrorCode.CAPTCHA_FAILED);
      case UNAVAILABLE -> throw new RegistrationRejectedException(ErrorCode.CAPTCHA_UNAVAILABLE);
      case PASSED -> {
        // continue
      }
    }
    RegistrationValidator.Validated valid = validator.validate(request);
    if (repository.existsByEmailNormalized(
        Registration.normalizeEmail(valid.participant().email()))) {
      throw new RegistrationRejectedException(ErrorCode.DUPLICATE_EMAIL);
    }
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Registration registration =
        new Registration(
            UUID.randomUUID(),
            valid.type(),
            valid.participant(),
            now,
            valid.options().stream().map(SelectedOption::of).toList(),
            valid.consents().stream().map(c -> new GivenConsent(c.id(), c.text(), now)).toList());
    String fileName = RegistrationCopy.fileName(registration);
    registration.assignJsonCopyFile(fileName);
    byte[] json = RegistrationCopy.serialize(registration);
    store(registration, fileName, json);
    return new Accepted(registration, json);
  }

  private void store(Registration registration, String fileName, byte[] json) {
    AtomicBoolean written = new AtomicBoolean();
    try {
      transaction.executeWithoutResult(
          status -> {
            repository.saveAndFlush(registration);
            try {
              copies.write(fileName, json);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
            written.set(true);
          });
    } catch (RuntimeException e) {
      if (written.get()) {
        deleteCopy(registration.id(), fileName);
      }
      if (e instanceof DataIntegrityViolationException && mentions(e, EMAIL_UNIQUE)) {
        throw new RegistrationRejectedException(ErrorCode.DUPLICATE_EMAIL, List.of());
      }
      LOG.warn(
          "registration {}: storage failed ({})", registration.id(), e.getClass().getSimpleName());
      throw new RegistrationRejectedException(ErrorCode.STORAGE_FAILED);
    }
  }

  private void deleteCopy(UUID id, String fileName) {
    try {
      copies.delete(fileName);
    } catch (IOException e) {
      LOG.error("registration {}: uncommitted JSON copy could not be removed", id);
    }
  }

  private static boolean mentions(Throwable e, String constraint) {
    Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
    return cause.getMessage() != null && cause.getMessage().contains(constraint);
  }
}
