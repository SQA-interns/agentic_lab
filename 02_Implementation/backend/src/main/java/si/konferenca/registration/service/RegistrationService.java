package si.konferenca.registration.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationRepository;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;

/**
 * Registration use case: business validation, anti-automation check, persistence with JSON backup
 * in one transaction, and email notifications after commit.
 */
@Service
public class RegistrationService {

  static final String FIELD_OPTION_IDS = "optionIds";
  static final String CODE_UNKNOWN_OPTION = "UNKNOWN_OPTION";
  static final String CODE_INACTIVE_OPTION = "INACTIVE_OPTION";
  static final String CODE_CONSENT_REQUIRED = "CONSENT_REQUIRED";

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

  private final RegistrationRepository repository;
  private final ConferenceOptionCatalog optionCatalog;
  private final CaptchaVerifier captchaVerifier;
  private final RegistrationBackupStore backupStore;
  private final RegistrationNotifier notifier;
  private final RegistrationJsonSerializer jsonSerializer;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  public RegistrationService(
      RegistrationRepository repository,
      ConferenceOptionCatalog optionCatalog,
      CaptchaVerifier captchaVerifier,
      RegistrationBackupStore backupStore,
      RegistrationNotifier notifier,
      RegistrationJsonSerializer jsonSerializer,
      TransactionTemplate transactionTemplate,
      Clock clock) {
    this.repository = repository;
    this.optionCatalog = optionCatalog;
    this.captchaVerifier = captchaVerifier;
    this.backupStore = backupStore;
    this.notifier = notifier;
    this.jsonSerializer = jsonSerializer;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
  }

  /**
   * Registers a participant.
   *
   * @throws RegistrationValidationException if options or consents are invalid
   * @throws CaptchaVerificationException if the anti-automation token is rejected
   */
  public RegistrationResult register(NewRegistration command) {
    List<FieldViolation> violations = new ArrayList<>();
    validateConsents(command, violations);
    List<SelectedOption> options = resolveOptions(command.optionIds(), violations);
    if (!violations.isEmpty()) {
      throw new RegistrationValidationException(violations);
    }
    verifyCaptcha(command);

    Registration registration = buildRegistration(command, options);
    byte[] json = jsonSerializer.toJson(RegistrationSnapshot.of(registration));
    transactionTemplate.executeWithoutResult(status -> persist(registration, json));
    LOG.info("Registration {} ({}) accepted", registration.getId(), registration.getType());

    sendNotifications(registration, json);
    return new RegistrationResult(
        registration.getId(), registration.getType(), registration.getCreatedAt());
  }

  private void validateConsents(NewRegistration command, List<FieldViolation> violations) {
    for (Consents.ConsentDefinition consent : Consents.ALL) {
      if (consent.mandatory() && !Boolean.TRUE.equals(command.consents().get(consent.id()))) {
        violations.add(new FieldViolation("consents." + consent.id(), CODE_CONSENT_REQUIRED));
      }
    }
  }

  private List<SelectedOption> resolveOptions(
      List<String> optionIds, List<FieldViolation> violations) {
    Set<String> uniqueIds = new LinkedHashSet<>(optionIds);
    Set<FieldViolation> optionViolations = new LinkedHashSet<>();
    List<SelectedOption> selected = new ArrayList<>();
    for (String id : uniqueIds) {
      Optional<ConferenceOption> option =
          id == null ? Optional.empty() : optionCatalog.findById(id);
      if (option.isEmpty()) {
        optionViolations.add(new FieldViolation(FIELD_OPTION_IDS, CODE_UNKNOWN_OPTION));
      } else if (!option.get().active()) {
        optionViolations.add(new FieldViolation(FIELD_OPTION_IDS, CODE_INACTIVE_OPTION));
      } else {
        selected.add(SelectedOption.of(option.get()));
      }
    }
    violations.addAll(optionViolations);
    return selected;
  }

  private void verifyCaptcha(NewRegistration command) {
    String token = command.captchaToken();
    if (token == null || token.isBlank() || !captchaVerifier.verify(token, command.clientIp())) {
      throw new CaptchaVerificationException();
    }
  }

  private Registration buildRegistration(NewRegistration command, List<SelectedOption> options) {
    UUID id = UUID.randomUUID();
    Instant createdAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    Registration.ParticipantName name =
        new Registration.ParticipantName(command.firstName(), command.lastName());
    boolean privacyConsent = Boolean.TRUE.equals(command.consents().get(Consents.PRIVACY));
    if (command.type() == RegistrationType.STUDENT) {
      return Registration.student(
          id,
          name,
          command.email(),
          new Registration.StudentDetails(
              command.studyInstitution(), command.studyProgramme(), command.studentId()),
          privacyConsent,
          createdAt,
          options);
    }
    return Registration.external(
        id, name, command.email(), command.organization(), privacyConsent, createdAt, options);
  }

  private void persist(Registration registration, byte[] json) {
    repository.saveAndFlush(registration);
    backupStore.store(registration.getId(), registration.getCreatedAt(), json);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              backupStore.delete(registration.getId(), registration.getCreatedAt());
            }
          }
        });
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private void sendNotifications(Registration registration, byte[] json) {
    RegistrationSnapshot snapshot = RegistrationSnapshot.of(registration);
    try {
      notifier.sendParticipantConfirmation(snapshot);
    } catch (RuntimeException e) {
      LOG.error(
          "Participant confirmation email for registration {} failed: {}",
          registration.getId(),
          e.getClass().getName());
    }
    try {
      notifier.sendOrganizerNotification(snapshot, json);
    } catch (RuntimeException e) {
      LOG.error(
          "Organizer notification for registration {} failed: {}",
          registration.getId(),
          e.getClass().getName());
    }
  }
}
