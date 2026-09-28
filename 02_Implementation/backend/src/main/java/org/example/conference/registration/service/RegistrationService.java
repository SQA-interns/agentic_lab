package org.example.conference.registration.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.example.conference.captcha.CaptchaVerifier;
import org.example.conference.registration.backup.BackupStore;
import org.example.conference.registration.domain.Registration;
import org.example.conference.registration.domain.RegistrationRepository;
import org.example.conference.shared.api.ApiException;
import org.example.conference.shared.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

/**
 * Acceptance workflow (spec §3, §5): validate, idempotency lookup, captcha, publish JSON, commit
 * DB. Success is returned only when both durable representations exist (AR-04).
 */
@Service
public class RegistrationService {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

  private final SelectionValidator validator;
  private final RawJsonFactory rawJsonFactory;
  private final RegistrationRepository repository;
  private final CaptchaVerifier captchaVerifier;
  private final BackupStore backupStore;
  private final RegistrationPersister persister;
  private final Clock clock;

  public RegistrationService(
      SelectionValidator validator,
      RawJsonFactory rawJsonFactory,
      RegistrationRepository repository,
      CaptchaVerifier captchaVerifier,
      BackupStore backupStore,
      RegistrationPersister persister,
      Clock clock) {
    this.validator = validator;
    this.rawJsonFactory = rawJsonFactory;
    this.repository = repository;
    this.captchaVerifier = captchaVerifier;
    this.backupStore = backupStore;
    this.persister = persister;
    this.clock = clock;
  }

  public RegistrationResult register(RegistrationCommand command, String clientIp) {
    ValidatedRegistration validated = validator.validate(command);
    String fingerprint = rawJsonFactory.fingerprint(validated);

    Optional<Registration> existing = findExisting(command.clientRequestId());
    if (existing.isPresent()) {
      return replayOrConflict(existing.get(), fingerprint);
    }
    if (!captchaVerifier.verify(command.captchaToken(), clientIp)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.CAPTCHA_INVALID);
    }

    UUID id = UUID.randomUUID();
    Instant submittedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
    String rawJson = rawJsonFactory.rawJson(id, submittedAt, validated);
    String sha256 = Hashing.sha256(rawJson);

    try {
      backupStore.publish(id, rawJson);
    } catch (IOException | UncheckedIOException | SecurityException e) {
      backupStore.discard(id);
      LOG.error("Backup publish failed for new registration {}: {}", id, e.getClass().getName());
      throw storageUnavailable(e);
    }
    try {
      persister.persist(id, submittedAt, fingerprint, rawJson, sha256, validated);
    } catch (DataIntegrityViolationException e) {
      backupStore.discard(id);
      Optional<Registration> winner = findExisting(command.clientRequestId());
      if (winner.isPresent()) {
        return replayOrConflict(winner.get(), fingerprint);
      }
      LOG.error("Registration {} rejected by database constraint", id);
      throw storageUnavailable(e);
    } catch (DataAccessException | TransactionException e) {
      backupStore.discard(id);
      LOG.error("Database commit failed for registration {}: {}", id, e.getClass().getName());
      throw storageUnavailable(e);
    }
    LOG.info("Registration {} accepted ({})", id, command.participantType());
    return new RegistrationResult(id, command.participantType(), submittedAt, false);
  }

  private Optional<Registration> findExisting(UUID clientRequestId) {
    try {
      return repository.findByClientRequestId(clientRequestId);
    } catch (DataAccessException | TransactionException e) {
      LOG.error("Idempotency lookup failed: {}", e.getClass().getName());
      throw storageUnavailable(e);
    }
  }

  private static RegistrationResult replayOrConflict(Registration existing, String fingerprint) {
    if (!existing.getRequestFingerprint().equals(fingerprint)) {
      throw new ApiException(HttpStatus.CONFLICT, ErrorCode.REQUEST_ID_CONFLICT);
    }
    return new RegistrationResult(
        existing.getId(), existing.getParticipantType(), existing.getCreatedAt(), true);
  }

  private static ApiException storageUnavailable(Exception cause) {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.STORAGE_UNAVAILABLE, cause);
  }
}
