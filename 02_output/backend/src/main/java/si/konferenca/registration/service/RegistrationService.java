package si.konferenca.registration.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.FieldError;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.SelectedOption;
import si.konferenca.registration.infrastructure.JsonCopyStore;
import si.konferenca.registration.infrastructure.RecaptchaVerifier;
import si.konferenca.registration.persistence.RegistrationStore;

/**
 * Accepts a registration (specification section 3): validate, verify the token, then store the
 * database row and the JSON copy in one transaction (AR-05). Only then is it accepted.
 */
@Service
public class RegistrationService {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);
  private static final String EMAIL_CONSTRAINT = "uq_registration_email";

  private final OptionCatalogueProvider catalogueProvider;
  private final RecaptchaVerifier recaptcha;
  private final RegistrationStore store;
  private final JsonCopyStore copies;
  private final TransactionTemplate transaction;
  private final Clock clock;

  @Autowired
  public RegistrationService(
      OptionCatalogueProvider catalogueProvider,
      RecaptchaVerifier recaptcha,
      RegistrationStore store,
      JsonCopyStore copies,
      TransactionTemplate transaction) {
    this(catalogueProvider, recaptcha, store, copies, transaction, Clock.systemUTC());
  }

  RegistrationService(
      OptionCatalogueProvider catalogueProvider,
      RecaptchaVerifier recaptcha,
      RegistrationStore store,
      JsonCopyStore copies,
      TransactionTemplate transaction,
      Clock clock) {
    this.catalogueProvider = catalogueProvider;
    this.recaptcha = recaptcha;
    this.store = store;
    this.copies = copies;
    this.transaction = transaction;
    this.clock = clock;
  }

  public Registration register(RegistrationCommand command, String clientAddress) {
    RegistrationValidator.Result valid =
        RegistrationValidator.validate(command, catalogueProvider.catalogue());
    if (!valid.errors().isEmpty()) {
      throw new RegistrationRejectedException(valid.errors());
    }
    if (!recaptcha.verify(command.recaptchaToken(), clientAddress)) {
      throw new RegistrationRejectedException(
          List.of(new FieldError("recaptchaToken", "captcha_failed")));
    }
    Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
    Registration registration =
        new Registration(
            UUID.randomUUID(),
            now,
            valid.type(),
            valid.participant(),
            valid.options().stream()
                .map(o -> new SelectedOption(o.id(), o.name(), o.category()))
                .toList(),
            valid.consents().stream().map(c -> new GivenConsent(c.id(), c.text(), now)).toList());
    store(registration);
    LOG.info("Registration {} accepted", registration.id());
    return registration;
  }

  private void store(Registration registration) {
    try {
      transaction.executeWithoutResult(
          status -> {
            if (store.emailRegistered(registration.participant().email())) {
              throw new DuplicateEmailException();
            }
            store.insert(registration);
            try {
              copies.write(registration);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    } catch (DuplicateEmailException e) {
      throw e;
    } catch (DataIntegrityViolationException e) {
      if (String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
          .contains(EMAIL_CONSTRAINT)) {
        throw new DuplicateEmailException();
      }
      throw failed(registration, e);
    } catch (UncheckedIOException | DataAccessException | TransactionException e) {
      throw failed(registration, e);
    }
  }

  /** Removes a copy written before a failed commit, so nothing partial remains (AC-005-03). */
  private StorageFailureException failed(Registration registration, RuntimeException cause) {
    LOG.error(
        "Registration {} could not be stored: {}", registration.id(), cause.getClass().getName());
    try {
      copies.delete(registration.id());
    } catch (IOException e) {
      LOG.error("JSON copy of registration {} could not be removed", registration.id());
    }
    return new StorageFailureException(cause);
  }
}
