package org.example.conference.registration.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** An accepted registration: the authoritative index record (AR-03). */
@Entity
@Table(name = "registration")
public class Registration {

  @Id private UUID id;

  @Column(name = "client_request_id", nullable = false, unique = true)
  private UUID clientRequestId;

  @Column(name = "request_fingerprint", nullable = false)
  private String requestFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(name = "participant_type", nullable = false)
  private ParticipantType participantType;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(nullable = false)
  private String email;

  private String organization;

  @Column(name = "study_institution")
  private String studyInstitution;

  @Column(name = "study_programme")
  private String studyProgramme;

  @Column(name = "student_id")
  private String studentId;

  @Column(name = "raw_json", nullable = false)
  private String rawJson;

  @Column(name = "raw_json_sha256", nullable = false)
  private String rawJsonSha256;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @OneToMany(
      mappedBy = "registration",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("optionGroup, position")
  private List<RegistrationSelection> selections = new ArrayList<>();

  @OneToMany(
      mappedBy = "registration",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("consentId")
  private List<RegistrationConsent> consents = new ArrayList<>();

  protected Registration() {}

  public Registration(
      UUID id,
      UUID clientRequestId,
      String requestFingerprint,
      ParticipantType participantType,
      Instant createdAt) {
    this.id = id;
    this.clientRequestId = clientRequestId;
    this.requestFingerprint = requestFingerprint;
    this.participantType = participantType;
    this.createdAt = createdAt;
  }

  public void setPersonalData(
      String firstName,
      String lastName,
      String email,
      String organization,
      String studyInstitution,
      String studyProgramme,
      String studentId) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.organization = organization;
    this.studyInstitution = studyInstitution;
    this.studyProgramme = studyProgramme;
    this.studentId = studentId;
  }

  public void setRawJson(String rawJson, String sha256) {
    this.rawJson = rawJson;
    this.rawJsonSha256 = sha256;
  }

  public void addSelection(String group, int position, String optionId, String optionName) {
    selections.add(new RegistrationSelection(this, group, position, optionId, optionName));
  }

  public void addConsent(String consentId, String consentText) {
    consents.add(new RegistrationConsent(this, consentId, consentText));
  }

  public UUID getId() {
    return id;
  }

  public UUID getClientRequestId() {
    return clientRequestId;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public ParticipantType getParticipantType() {
    return participantType;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getEmail() {
    return email;
  }

  public String getOrganization() {
    return organization;
  }

  public String getStudyInstitution() {
    return studyInstitution;
  }

  public String getStudyProgramme() {
    return studyProgramme;
  }

  public String getStudentId() {
    return studentId;
  }

  public String getRawJson() {
    return rawJson;
  }

  public String getRawJsonSha256() {
    return rawJsonSha256;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<RegistrationSelection> getSelections() {
    return List.copyOf(selections);
  }

  public List<RegistrationConsent> getConsents() {
    return List.copyOf(consents);
  }
}
