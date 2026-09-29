package si.konferenca.registration.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationSnapshot;
import si.konferenca.registration.integration.JsonBackupStore;
import si.konferenca.registration.integration.MailNotifier;
import si.konferenca.registration.integration.RecaptchaVerifier;
import si.konferenca.registration.persistence.ConferenceOptionRepository;
import si.konferenca.registration.persistence.RegistrationRepository;
import si.konferenca.registration.service.RegistrationExceptions.RecaptchaFailedException;
import si.konferenca.registration.service.RegistrationExceptions.RegistrationNotSavedException;
import si.konferenca.registration.service.RegistrationValidator.ValidRegistration;

/**
 * The registration write path (specification §10): verify, validate, store in the database and the
 * JSON backup together, then notify by email. Only the registration id is logged.
 */
public class RegistrationService {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

  private final RecaptchaVerifier recaptcha;
  private final RegistrationValidator validator;
  private final OptionCatalogService catalog;
  private final RegistrationRepository registrations;
  private final ConferenceOptionRepository options;
  private final JsonBackupStore backups;
  private final MailNotifier mail;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public RegistrationService(
      RecaptchaVerifier recaptcha,
      RegistrationValidator validator,
      OptionCatalogService catalog,
      RegistrationRepository registrations,
      ConferenceOptionRepository options,
      JsonBackupStore backups,
      MailNotifier mail,
      TransactionTemplate transactions,
      Clock clock) {
    this.recaptcha = recaptcha;
    this.validator = validator;
    this.catalog = catalog;
    this.registrations = registrations;
    this.options = options;
    this.backups = backups;
    this.mail = mail;
    this.transactions = transactions;
    this.clock = clock;
  }

  /**
   * Accepts a registration or throws one of {@link RegistrationExceptions}; in every exceptional
   * case nothing is stored.
   */
  public RegistrationSnapshot register(RegistrationCommand command, String remoteIp) {
    if (!recaptcha.verify(command.recaptchaToken(), remoteIp)) {
      throw new RecaptchaFailedException();
    }
    ValidRegistration valid = validator.validate(command, catalog.catalog());

    UUID id = UUID.randomUUID();
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Stored stored;
    try {
      stored = Objects.requireNonNull(transactions.execute(status -> store(id, valid, now)));
    } catch (RuntimeException e) {
      backups.deleteQuietly(id);
      // Exception type only: database messages can echo the rejected row (personal data).
      LOG.error("Registration {} could not be stored ({})", id, e.getClass().getName());
      throw new RegistrationNotSavedException(e);
    }
    LOG.info("Registration {} accepted", id);
    notifyByEmail(stored.snapshot(), stored.rawJson());
    return stored.snapshot();
  }

  private record Stored(RegistrationSnapshot snapshot, byte[] rawJson) {}

  /** Inserts the registration and writes its JSON backup inside one database transaction. */
  private Stored store(UUID id, ValidRegistration valid, Instant now) {
    List<ConferenceOption> managed =
        valid.options().stream().map(o -> options.getReferenceById(o.getId())).toList();
    Registration registration =
        registrations.saveAndFlush(
            new Registration(id, valid.type(), valid.participant(), now, now, managed));
    RegistrationSnapshot snapshot = RegistrationSnapshot.of(registration);
    try {
      byte[] rawJson = backups.serialize(snapshot);
      backups.write(id, rawJson);
      return new Stored(snapshot, rawJson);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void notifyByEmail(RegistrationSnapshot snapshot, byte[] rawJson) {
    try {
      mail.sendParticipantConfirmation(snapshot);
    } catch (Exception e) {
      LOG.warn(
          "Participant confirmation for registration {} could not be sent",
          snapshot.registrationId());
    }
    try {
      mail.sendOrganizerNotification(snapshot, rawJson);
    } catch (Exception e) {
      LOG.warn(
          "Organizer notification for registration {} could not be sent",
          snapshot.registrationId());
    }
  }
}
