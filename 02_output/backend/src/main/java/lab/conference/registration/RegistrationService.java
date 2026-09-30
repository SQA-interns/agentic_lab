package lab.conference.registration;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lab.conference.notifications.Hashes;
import lab.conference.notifications.NotificationService;
import lab.conference.platform.ApiException;
import lab.conference.platform.FieldError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Acceptance flow (spec section 4): validate, replay check, captcha, publish JSON, then one DB
 * transaction with the registration and notification intents. Success is returned only after both
 * durable representations exist (BR-05, AR-04, AR-09).
 */
@Service
public class RegistrationService {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);
  private final RegistrationValidator validator;
  private final CaptchaVerifier captcha;
  private final RegistrationRepository repository;
  private final JsonStore store;
  private final NotificationService notifications;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final List<String> organizerEmails;

  public RegistrationService(
      RegistrationValidator validator,
      CaptchaVerifier captcha,
      RegistrationRepository repository,
      JsonStore store,
      NotificationService notifications,
      TransactionTemplate tx,
      Clock clock,
      OrganizerRecipients organizerRecipients) {
    this.validator = validator;
    this.captcha = captcha;
    this.repository = repository;
    this.store = store;
    this.notifications = notifications;
    this.tx = tx;
    this.clock = clock;
    this.organizerEmails = organizerRecipients.addresses();
  }

  public AcceptanceResult accept(FormType form, JsonNode body, String remoteAddress) {
    ValidatedRegistration r = validator.validate(form, body);
    Optional<AcceptanceResult> replay = replay(r);
    if (replay.isPresent()) {
      return replay.get();
    }
    if (!captcha.verify(r.captchaToken(), remoteAddress)) {
      throw ApiException.validation(
          List.of(
              new FieldError(
                  "captchaToken",
                  "CAPTCHA_FAILED",
                  "Captcha verification failed. Please try again.")));
    }
    UUID id = UUID.randomUUID();
    Instant acceptedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
    byte[] json = RegistrationRecord.toJson(id, acceptedAt, r);
    try {
      store.publish(id, json);
    } catch (IOException e) {
      LOG.error(
          "JSON store write failed for registration {} ({})", id, e.getClass().getSimpleName());
      throw ApiException.unavailable(e);
    }
    try {
      String sha = Hashes.sha256(json);
      tx.executeWithoutResult(
          status -> {
            repository.saveAndFlush(new RegistrationEntity(id, acceptedAt, r, sha));
            notifications.enqueue(MailComposer.compose(id, r, sha, organizerEmails));
          });
      store.settled(id);
      LOG.info("Registration {} accepted ({})", id, form.key());
      return new AcceptanceResult(id, r.clientRequestId(), form, acceptedAt, false);
    } catch (DataIntegrityViolationException e) {
      store.discard(id);
      return replay(r).orElseThrow(() -> ApiException.unavailable(e));
    } catch (DataAccessException | TransactionException e) {
      store.discard(id);
      LOG.error(
          "Database commit failed for registration {} ({})", id, e.getClass().getSimpleName());
      throw ApiException.unavailable(e);
    }
  }

  private Optional<AcceptanceResult> replay(ValidatedRegistration r) {
    Optional<RegistrationEntity> existing;
    try {
      existing = repository.findByClientRequestId(r.clientRequestId());
    } catch (DataAccessException | TransactionException e) {
      LOG.error("Database unavailable during replay check ({})", e.getClass().getSimpleName());
      throw ApiException.unavailable(e);
    }
    if (existing.isEmpty()) {
      return Optional.empty();
    }
    RegistrationEntity e = existing.get();
    if (!e.requestFingerprint().equals(r.fingerprint())) {
      throw ApiException.conflict("This client request ID was already used for different content.");
    }
    return Optional.of(
        new AcceptanceResult(
            e.id(), e.clientRequestId(), FormType.fromKey(e.formType()), e.acceptedAt(), true));
  }
}
