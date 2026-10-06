package si.konferenca.registration.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import si.konferenca.registration.domain.Registration;

/** Row of the registration table (database.sql). */
@Entity
@Table(name = "registration")
public class RegistrationEntity {

  @Id private UUID id;

  @Column(name = "registration_type", nullable = false, length = 16)
  private String registrationType;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "email_normalized", nullable = false, length = 254)
  private String emailNormalized;

  @Column(length = 200)
  private String organization;

  @Column(name = "study_institution", length = 200)
  private String studyInstitution;

  @Column(name = "study_programme", length = 200)
  private String studyProgramme;

  @Column(name = "student_id", length = 50)
  private String studentId;

  @Column(name = "consent_id", nullable = false, length = 64)
  private String consentId;

  @Column(name = "consent_text", nullable = false, columnDefinition = "text")
  private String consentText;

  @Column(name = "consent_given_at", nullable = false)
  private Instant consentGivenAt;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(name = "json_copy_file", nullable = false, length = 255)
  private String jsonCopyFile;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"))
  private List<RegistrationOptionValue> options = new ArrayList<>();

  protected RegistrationEntity() {}

  /** The row for an accepted registration; consent is given at acceptance. */
  static RegistrationEntity from(Registration r, String jsonCopyFile) {
    RegistrationEntity e = new RegistrationEntity();
    e.id = r.id();
    e.registrationType = r.type().name();
    e.firstName = r.firstName();
    e.lastName = r.lastName();
    e.email = r.email();
    e.emailNormalized = r.normalizedEmail();
    e.organization = r.organization();
    e.studyInstitution = r.studyInstitution();
    e.studyProgramme = r.studyProgramme();
    e.studentId = r.studentId();
    e.consentId = r.consent().id();
    e.consentText = r.consent().text();
    e.consentGivenAt = r.receivedAt();
    e.receivedAt = r.receivedAt();
    e.jsonCopyFile = jsonCopyFile;
    e.options =
        new ArrayList<>(
            r.options().stream()
                .map(o -> new RegistrationOptionValue(o.id(), o.name(), o.category().name()))
                .toList());
    return e;
  }

  UUID id() {
    return id;
  }

  String registrationType() {
    return registrationType;
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

  String consentId() {
    return consentId;
  }

  String consentText() {
    return consentText;
  }

  Instant receivedAt() {
    return receivedAt;
  }

  List<RegistrationOptionValue> options() {
    return List.copyOf(options);
  }
}
