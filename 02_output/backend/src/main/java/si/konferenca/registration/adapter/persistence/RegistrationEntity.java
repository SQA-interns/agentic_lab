package si.konferenca.registration.adapter.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Table {@code registration} of docs/02_contracts/database.sql. */
@Entity
@Table(name = "registration")
public class RegistrationEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 10)
  private String type;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "email_normalized", nullable = false, length = 254, unique = true)
  private String emailNormalized;

  @Column(length = 200)
  private String organization;

  @Column(name = "study_institution", length = 200)
  private String studyInstitution;

  @Column(name = "study_programme", length = 200)
  private String studyProgramme;

  @Column(name = "student_id", length = 50)
  private String studentId;

  @Column(name = "submitted_at", nullable = false)
  private Instant submittedAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"))
  @OrderBy("optionId ASC")
  private List<OptionColumns> options = new ArrayList<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "registration_consent",
      joinColumns = @JoinColumn(name = "registration_id"))
  @OrderBy("consentId ASC")
  private List<ConsentColumns> consents = new ArrayList<>();

  protected RegistrationEntity() {}

  RegistrationEntity(
      UUID id,
      String type,
      String firstName,
      String lastName,
      String email,
      String emailNormalized,
      Instant submittedAt) {
    this.id = id;
    this.type = type;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.emailNormalized = emailNormalized;
    this.submittedAt = submittedAt;
  }

  void setTypeFields(
      String organization, String studyInstitution, String studyProgramme, String studentId) {
    this.organization = organization;
    this.studyInstitution = studyInstitution;
    this.studyProgramme = studyProgramme;
    this.studentId = studentId;
  }

  UUID id() {
    return id;
  }

  String type() {
    return type;
  }

  String firstName() {
    return firstName;
  }

  String lastName() {
    return lastName;
  }

  String email() {
    return email;
  }

  String organization() {
    return organization;
  }

  String studyInstitution() {
    return studyInstitution;
  }

  String studyProgramme() {
    return studyProgramme;
  }

  String studentId() {
    return studentId;
  }

  Instant submittedAt() {
    return submittedAt;
  }

  List<OptionColumns> options() {
    return options;
  }

  List<ConsentColumns> consents() {
    return consents;
  }
}
