package org.example.conference.registration.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.conference.catalog.CatalogOption;
import org.example.conference.catalog.OptionGroup;
import org.example.conference.notification.NotificationService;
import org.example.conference.registration.domain.ParticipantType;
import org.example.conference.registration.domain.Registration;
import org.example.conference.registration.domain.RegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Single DB transaction: registration, selections, consents and email outbox (AR-04, AR-05). */
@Component
public class RegistrationPersister {

  private final RegistrationRepository repository;
  private final NotificationService notificationService;
  private final RegistrationEmailComposer composer;

  public RegistrationPersister(
      RegistrationRepository repository,
      NotificationService notificationService,
      RegistrationEmailComposer composer) {
    this.repository = repository;
    this.notificationService = notificationService;
    this.composer = composer;
  }

  @Transactional
  public void persist(
      UUID id,
      Instant submittedAt,
      String fingerprint,
      String rawJson,
      String sha256,
      ValidatedRegistration v) {
    RegistrationCommand command = v.command();
    Registration registration =
        new Registration(
            id, command.clientRequestId(), fingerprint, command.participantType(), submittedAt);
    Map<String, String> f = command.fields();
    boolean student = command.participantType() == ParticipantType.STUDENT;
    registration.setPersonalData(
        f.get("firstName"),
        f.get("lastName"),
        f.get("email"),
        student ? null : f.get("organization"),
        student ? f.get("studyInstitution") : null,
        student ? f.get("studyProgramme") : null,
        student ? f.get("studentId") : null);
    registration.setRawJson(rawJson, sha256);
    for (OptionGroup group : OptionGroup.values()) {
      List<CatalogOption> options = v.selections().get(group);
      for (int i = 0; i < options.size(); i++) {
        registration.addSelection(group.key(), i, options.get(i).id(), options.get(i).name());
      }
    }
    v.grantedConsents().forEach(c -> registration.addConsent(c.id(), c.text()));
    repository.saveAndFlush(registration);
    notificationService.enqueue(
        composer.compose(id, v, rawJson, notificationService.organizerRecipients()));
  }
}
