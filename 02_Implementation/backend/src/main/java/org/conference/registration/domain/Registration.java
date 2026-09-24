package org.conference.registration.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A stored conference registration (US-005). */
@Entity
@Table(name = "registration")
public class Registration {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RegistrationType type;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(length = 200)
  private String organization;

  @Column(name = "study_institution", length = 200)
  private String studyInstitution;

  @Column(name = "study_programme", length = 200)
  private String studyProgramme;

  @Column(name = "student_id", length = 50)
  private String studentId;

  @Column(name = "privacy_consent", nullable = false)
  private boolean privacyConsent;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "backup_file", nullable = false, length = 255)
  private String backupFile;

  @ElementCollection
  @CollectionTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"))
  @OrderBy("optionId")
  private List<SelectedOption> options = new ArrayList<>();

  protected Registration() {}

  public Registration(
      UUID id,
      RegistrationType type,
      ParticipantDetails participant,
      boolean privacyConsent,
      List<SelectedOption> options,
      Instant createdAt,
      String backupFile) {
    this.id = id;
    this.type = type;
    this.firstName = participant.firstName();
    this.lastName = participant.lastName();
    this.email = participant.email();
    this.organization = participant.organization();
    this.studyInstitution = participant.studyInstitution();
    this.studyProgramme = participant.studyProgramme();
    this.studentId = participant.studentId();
    this.privacyConsent = privacyConsent;
    this.options = new ArrayList<>(options);
    this.createdAt = createdAt;
    this.backupFile = backupFile;
  }

  public UUID getId() {
    return id;
  }

  public RegistrationType getType() {
    return type;
  }

  public ParticipantDetails getParticipant() {
    return new ParticipantDetails(
        firstName, lastName, email, organization, studyInstitution, studyProgramme, studentId);
  }

  public boolean isPrivacyConsent() {
    return privacyConsent;
  }

  public List<SelectedOption> getOptions() {
    return List.copyOf(options);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getBackupFile() {
    return backupFile;
  }
}
