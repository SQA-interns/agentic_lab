package si.konferenca.registration.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** An accepted conference registration of an external participant or a student. */
@Entity
@Table(name = "registration")
public class Registration {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "registration_type", nullable = false, length = 16)
  private RegistrationType type;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "email", nullable = false, length = 254)
  private String email;

  @Column(name = "organization", length = 200)
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

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"))
  @OrderBy("optionId ASC")
  private List<SelectedOption> options = new ArrayList<>();

  protected Registration() {
    // for JPA
  }

  private Registration(
      UUID id,
      RegistrationType type,
      ParticipantName name,
      String email,
      boolean privacyConsent,
      Instant createdAt,
      List<SelectedOption> options) {
    this.id = id;
    this.type = type;
    this.firstName = name.firstName();
    this.lastName = name.lastName();
    this.email = email;
    this.privacyConsent = privacyConsent;
    this.createdAt = createdAt;
    this.options = new ArrayList<>(options);
  }

  /** First and last name of a participant. */
  public record ParticipantName(String firstName, String lastName) {}

  public static Registration external(
      UUID id,
      ParticipantName name,
      String email,
      String organization,
      boolean privacyConsent,
      Instant createdAt,
      List<SelectedOption> options) {
    Registration registration =
        new Registration(
            id, RegistrationType.EXTERNAL, name, email, privacyConsent, createdAt, options);
    registration.organization = organization;
    return registration;
  }

  public static Registration student(
      UUID id,
      ParticipantName name,
      String email,
      StudentDetails details,
      boolean privacyConsent,
      Instant createdAt,
      List<SelectedOption> options) {
    Registration registration =
        new Registration(
            id, RegistrationType.STUDENT, name, email, privacyConsent, createdAt, options);
    registration.studyInstitution = details.studyInstitution();
    registration.studyProgramme = details.studyProgramme();
    registration.studentId = details.studentId();
    return registration;
  }

  /** Student-specific fixed fields. */
  public record StudentDetails(String studyInstitution, String studyProgramme, String studentId) {}

  public UUID getId() {
    return id;
  }

  public RegistrationType getType() {
    return type;
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

  public boolean isPrivacyConsent() {
    return privacyConsent;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<SelectedOption> getOptions() {
    return Collections.unmodifiableList(options);
  }
}
