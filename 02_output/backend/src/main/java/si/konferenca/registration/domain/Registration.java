package si.konferenca.registration.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** An accepted registration (docs/02_contracts/database.sql). */
@Entity
@Table(name = "registration")
public class Registration {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 8)
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

  @Column(name = "registered_at", nullable = false)
  private Instant registeredAt;

  @ElementCollection
  @CollectionTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"))
  private List<SelectedOption> options = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "registration_consent",
      joinColumns = @JoinColumn(name = "registration_id"))
  private List<GivenConsent> consents = new ArrayList<>();

  protected Registration() {}

  public Registration(
      UUID id,
      RegistrationType type,
      ParticipantDetails participant,
      Instant registeredAt,
      List<SelectedOption> options,
      List<GivenConsent> consents) {
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
    this.registeredAt = registeredAt;
    this.options = new ArrayList<>(options);
    this.consents = new ArrayList<>(consents);
  }

  /** The form of an email address used for the one-registration-per-address rule (D-13). */
  public static String normalizeEmail(String email) {
    return email.toLowerCase(Locale.ROOT);
  }

  public UUID id() {
    return id;
  }

  public RegistrationType type() {
    return type;
  }

  public ParticipantDetails participant() {
    return new ParticipantDetails(
        firstName, lastName, email, organization, studyInstitution, studyProgramme, studentId);
  }

  public Instant registeredAt() {
    return registeredAt;
  }

  public List<SelectedOption> options() {
    return List.copyOf(options);
  }

  public List<GivenConsent> consents() {
    return List.copyOf(consents);
  }
}
