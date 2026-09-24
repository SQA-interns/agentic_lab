package org.conference.registration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.conference.registration.domain.ConferenceOption;
import org.conference.registration.domain.Registration;
import org.conference.registration.domain.SelectedOption;
import org.conference.registration.repository.RegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Registration use case (US-001, US-002, US-005): anti-bot checks, option resolution, relational
 * persistence and JSON backup in one unit of work. Emails follow after commit via {@link
 * RegistrationCompletedEvent}.
 */
@Service
public class RegistrationService {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

  private final RegistrationRepository repository;
  private final OptionCatalog optionCatalog;
  private final FormTokenService formTokenService;
  private final BackupWriter backupWriter;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public RegistrationService(
      RegistrationRepository repository,
      OptionCatalog optionCatalog,
      FormTokenService formTokenService,
      BackupWriter backupWriter,
      ObjectMapper objectMapper,
      ApplicationEventPublisher events,
      Clock clock) {
    this.repository = repository;
    this.optionCatalog = optionCatalog;
    this.formTokenService = formTokenService;
    this.backupWriter = backupWriter;
    this.objectMapper = objectMapper;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public RegistrationResult register(RegistrationCommand command) {
    if (command.honeypot() != null && !command.honeypot().isEmpty()) {
      throw new SubmissionRejectedException("honeypot field filled");
    }
    FormTokenService.VerifiedToken token = formTokenService.verify(command.formToken());
    if (!command.privacyConsent()) {
      throw new RegistrationValidationException(
          "privacyConsent", "You must accept the privacy statement.");
    }
    List<ConferenceOption> options = optionCatalog.resolve(command.type(), command.optionIds());

    UUID id = UUID.randomUUID();
    Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
    String backupFile = BackupWriter.fileNameFor(id, createdAt);
    Registration registration =
        new Registration(
            id,
            command.type(),
            command.participant(),
            true,
            options.stream().map(SelectedOption::of).toList(),
            createdAt,
            backupFile);
    repository.saveAndFlush(registration);

    BackupDocument document = BackupDocument.of(registration);
    byte[] json = toJson(document);
    backupWriter.write(backupFile, json);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              backupWriter.delete(backupFile);
            }
          }
        });

    formTokenService.consume(token);
    events.publishEvent(new RegistrationCompletedEvent(document, json));
    LOG.info("Registration {} stored (type {})", id, command.type());
    return new RegistrationResult(id, command.type(), createdAt);
  }

  private byte[] toJson(BackupDocument document) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
    } catch (JsonProcessingException e) {
      throw new BackupFailedException("Could not serialise registration backup", e);
    }
  }
}
