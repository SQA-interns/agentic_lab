package si.konferenca.registration.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/** An accepted registration (BR-07); mapped to the tables of specification section 6. */
@Entity
@Table(name = "registration")
public class Registration {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "reference", nullable = false, unique = true, updatable = false)
  private UUID reference;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 16)
  private RegistrationType type;

  @Column(name = "first_name", nullable = false, length = 200)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 200)
  private String lastName;

  @Column(name = "email", nullable = false, length = 254)
  private String email;

  @Column(name = "organization", length = 200)
  private String organization;

  @Column(name = "study_institution", length = 200)
  private String studyInstitution;

  @Column(name = "study_programme", length = 200)
  private String studyProgramme;

  @Column(name = "student_id", length = 200)
  private String studentId;

  @Column(name = "submitted_at", nullable = false, updatable = false)
  private Instant submittedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "participant_mail_status", nullable = false, length = 16)
  private MailStatus participantMailStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "organizer_mail_status", nullable = false, length = 16)
  private MailStatus organizerMailStatus;

  @Column(name = "mail_attempts", nullable = false)
  private int mailAttempts;

  @Column(name = "last_mail_attempt_at")
  private Instant lastMailAttemptAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @Fetch(FetchMode.SUBSELECT)
  @CollectionTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"))
  private List<RegistrationOption> options = new ArrayList<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @Fetch(FetchMode.SUBSELECT)
  @CollectionTable(
      name = "registration_consent",
      joinColumns = @JoinColumn(name = "registration_id"))
  private List<RegistrationConsent> consents = new ArrayList<>();

  protected Registration() {}

  /** Creates a new accepted registration whose emails are still to be sent. */
  public static Registration accept(
      UUID reference,
      ParticipantDetails participant,
      List<ConferenceOption> options,
      List<ConsentDefinition> consents,
      Instant now) {
    Registration r = new Registration();
    r.reference = reference;
    r.type = participant.type();
    r.firstName = participant.firstName();
    r.lastName = participant.lastName();
    r.email = participant.email();
    r.organization = participant.organization();
    r.studyInstitution = participant.studyInstitution();
    r.studyProgramme = participant.studyProgramme();
    r.studentId = participant.studentId();
    r.submittedAt = now;
    r.participantMailStatus = MailStatus.PENDING;
    r.organizerMailStatus = MailStatus.PENDING;
    options.forEach(o -> r.options.add(new RegistrationOption(o)));
    consents.forEach(c -> r.consents.add(new RegistrationConsent(c, now)));
    return r;
  }

  /** Records the outcome of one delivery attempt of both emails (D-11). */
  public void recordMailAttempt(
      MailStatus participant, MailStatus organizer, Instant at, int maxAttempts) {
    mailAttempts++;
    lastMailAttemptAt = at;
    participantMailStatus = giveUpIfExhausted(participant, maxAttempts);
    organizerMailStatus = giveUpIfExhausted(organizer, maxAttempts);
  }

  private MailStatus giveUpIfExhausted(MailStatus status, int maxAttempts) {
    return status == MailStatus.FAILED && mailAttempts >= maxAttempts
        ? MailStatus.ABANDONED
        : status;
  }

  public boolean mailPending() {
    return participantMailStatus.needsSending() || organizerMailStatus.needsSending();
  }

  public Long id() {
    return id;
  }

  public UUID reference() {
    return reference;
  }

  public RegistrationType type() {
    return type;
  }

  public String firstName() {
    return firstName;
  }

  public String lastName() {
    return lastName;
  }

  public String email() {
    return email;
  }

  public String organization() {
    return organization;
  }

  public String studyInstitution() {
    return studyInstitution;
  }

  public String studyProgramme() {
    return studyProgramme;
  }

  public String studentId() {
    return studentId;
  }

  public Instant submittedAt() {
    return submittedAt;
  }

  public MailStatus participantMailStatus() {
    return participantMailStatus;
  }

  public MailStatus organizerMailStatus() {
    return organizerMailStatus;
  }

  public int mailAttempts() {
    return mailAttempts;
  }

  public Instant lastMailAttemptAt() {
    return lastMailAttemptAt;
  }

  public List<RegistrationOption> options() {
    return List.copyOf(options);
  }

  public List<RegistrationConsent> consents() {
    return List.copyOf(consents);
  }
}
