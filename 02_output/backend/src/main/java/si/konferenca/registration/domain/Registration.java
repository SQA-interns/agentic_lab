package si.konferenca.registration.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** An accepted registration (BR-07); personal data per security-requirements.md. */
@Entity
@Table(name = "registration")
public class Registration implements Persistable<UUID> {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private RegistrationType type;

  @Column(name = "submitted_at", nullable = false)
  private Instant submittedAt;

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

  @ElementCollection
  @CollectionTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"))
  @OrderColumn(name = "position")
  private List<SelectedOption> options = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "registration_consent",
      joinColumns = @JoinColumn(name = "registration_id"))
  @OrderBy("consentId")
  private List<GivenConsent> consents = new ArrayList<>();

  @Transient private boolean fresh = true;

  protected Registration() {}

  /** Participant details; exactly the fields of the registration type are non-null. */
  public record Details(
      RegistrationType type,
      String firstName,
      String lastName,
      String email,
      String organization,
      String studyInstitution,
      String studyProgramme,
      String studentId) {}

  public Registration(
      UUID id,
      Instant submittedAt,
      Details details,
      List<SelectedOption> options,
      List<GivenConsent> consents) {
    this.id = id;
    this.submittedAt = submittedAt;
    this.type = details.type();
    this.firstName = details.firstName();
    this.lastName = details.lastName();
    this.email = details.email();
    this.organization = details.organization();
    this.studyInstitution = details.studyInstitution();
    this.studyProgramme = details.studyProgramme();
    this.studentId = details.studentId();
    this.options = new ArrayList<>(options);
    this.consents = new ArrayList<>(consents);
  }

  @Override
  public UUID getId() {
    return id;
  }

  /** New until persisted or loaded, so saving inserts without a prior select. */
  @Override
  public boolean isNew() {
    return fresh;
  }

  @PostPersist
  @PostLoad
  void markStored() {
    fresh = false;
  }

  public RegistrationType getType() {
    return type;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
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

  public List<SelectedOption> getOptions() {
    return Collections.unmodifiableList(options);
  }

  public List<GivenConsent> getConsents() {
    return Collections.unmodifiableList(consents);
  }
}
