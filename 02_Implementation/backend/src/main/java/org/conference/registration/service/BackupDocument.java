package org.conference.registration.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.conference.registration.domain.OptionCategory;
import org.conference.registration.domain.ParticipantDetails;
import org.conference.registration.domain.Registration;
import org.conference.registration.domain.RegistrationType;

/**
 * JSON representation of a registration, written as backup file and attached to the organizer
 * notification (US-005, US-007). Contains everything needed to recover the registration.
 */
public record BackupDocument(
    int schemaVersion,
    UUID id,
    RegistrationType type,
    Instant createdAt,
    Participant participant,
    boolean privacyConsent,
    List<Option> options) {

  public static final int SCHEMA_VERSION = 1;

  public BackupDocument {
    options = List.copyOf(options);
  }

  public static BackupDocument of(Registration registration) {
    ParticipantDetails p = registration.getParticipant();
    return new BackupDocument(
        SCHEMA_VERSION,
        registration.getId(),
        registration.getType(),
        registration.getCreatedAt(),
        new Participant(
            p.firstName(),
            p.lastName(),
            p.email(),
            p.organization(),
            p.studyInstitution(),
            p.studyProgramme(),
            p.studentId()),
        registration.isPrivacyConsent(),
        registration.getOptions().stream()
            .map(o -> new Option(o.getOptionId(), o.getCategory(), o.getDisplayName()))
            .toList());
  }

  /** Participant fields; fields of the other variant are omitted. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Participant(
      String firstName,
      String lastName,
      String email,
      String organization,
      String studyInstitution,
      String studyProgramme,
      String studentId) {}

  /** A selected option in the backup. */
  public record Option(String id, OptionCategory category, String name) {}
}
