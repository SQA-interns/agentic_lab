package si.konferenca.registration.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.ListIndexJdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An accepted registration (registration-storage.sql). Values are stored as accepted. */
@Entity
@Table(name = "registration")
public class Registration {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 16)
  private RegistrationType type;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "email", nullable = false, length = 254)
  private String email;

  @Column(name = "email_normalized", nullable = false, length = 254)
  private String emailNormalized;

  @Column(name = "organization", length = 200)
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

  @Column(name = "accepted_at", nullable = false)
  private Instant acceptedAt;

  @ElementCollection
  @CollectionTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"))
  @OrderColumn(name = "position")
  @ListIndexJdbcTypeCode(SqlTypes.SMALLINT)
  private List<SelectedOption> options = new ArrayList<>();

  protected Registration() {}

  public Registration(
      UUID id,
      RegistrationType type,
      Participant participant,
      List<SelectedOption> options,
      String consentId,
      String consentText,
      Instant acceptedAt) {
    this.id = id;
    this.type = type;
    this.firstName = participant.firstName();
    this.lastName = participant.lastName();
    this.email = participant.email();
    this.emailNormalized = normalizeEmail(participant.email());
    this.organization = participant.organization();
    this.studyInstitution = participant.studyInstitution();
    this.studyProgramme = participant.studyProgramme();
    this.studentId = participant.studentId();
    this.options = new ArrayList<>(options);
    this.consentId = consentId;
    this.consentText = consentText;
    this.consentGivenAt = acceptedAt;
    this.acceptedAt = acceptedAt;
  }

  /** Uniqueness key of an email address (D-10): trimmed and lower-cased. */
  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  public UUID id() {
    return id;
  }

  public RegistrationType type() {
    return type;
  }

  public Participant participant() {
    return new Participant(
        firstName, lastName, email, organization, studyInstitution, studyProgramme, studentId);
  }

  public List<SelectedOption> options() {
    return List.copyOf(options);
  }

  public String consentId() {
    return consentId;
  }

  public String consentText() {
    return consentText;
  }

  public Instant consentGivenAt() {
    return consentGivenAt;
  }

  public Instant acceptedAt() {
    return acceptedAt;
  }
}
