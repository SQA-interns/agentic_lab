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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** An accepted registration (`registration-storage.sql`, BR-01, BR-07). */
@Entity
@Table(name = "registration")
public class Registration {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RegistrationType type;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(nullable = false)
  private String email;

  @Column(name = "email_normalized", nullable = false)
  private String emailNormalized;

  private String organization;

  @Column(name = "study_institution")
  private String studyInstitution;

  @Column(name = "study_programme")
  private String studyProgramme;

  @Column(name = "student_id")
  private String studentId;

  @Column(name = "registered_at", nullable = false)
  private Instant registeredAt;

  @Column(name = "json_copy_file", nullable = false)
  private String jsonCopyFile;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"))
  @OrderColumn(name = "position")
  private List<SelectedOption> options = new ArrayList<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "registration_consent",
      joinColumns = @JoinColumn(name = "registration_id"))
  private List<GivenConsent> consents = new ArrayList<>();

  protected Registration() {}

  /** A new registration with trimmed values; organization xor study fields per type. */
  public Registration(
      UUID id,
      RegistrationType type,
      Participant participant,
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

  /** The duplicate key of D-15: trimmed and lower-cased. */
  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  public void assignJsonCopyFile(String fileName) {
    this.jsonCopyFile = fileName;
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
