package si.konferenca.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** An accepted registration of either type (specification §3.2). */
@Entity
@Table(name = "registration")
public class Registration {

  /** Order in which options are listed: by set, then by position in the options file. */
  public static final Comparator<ConferenceOption> OPTION_ORDER =
      Comparator.comparing(ConferenceOption::getCategory)
          .thenComparingInt(ConferenceOption::getSortOrder)
          .thenComparing(ConferenceOption::getId);

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

  @Column(name = "personal_data_consent_at", nullable = false)
  private Instant personalDataConsentAt;

  @Column(name = "submitted_at", nullable = false)
  private Instant submittedAt;

  @ManyToMany
  @JoinTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"),
      inverseJoinColumns = @JoinColumn(name = "option_id"))
  private Set<ConferenceOption> options = new LinkedHashSet<>();

  protected Registration() {}

  /** Participant data of a registration; which fields are set depends on the type. */
  public record Participant(
      String firstName,
      String lastName,
      String email,
      String organization,
      String studyInstitution,
      String studyProgramme,
      String studentId) {}

  public Registration(
      UUID id,
      RegistrationType type,
      Participant participant,
      Instant personalDataConsentAt,
      Instant submittedAt,
      Collection<ConferenceOption> options) {
    this.id = id;
    this.type = type;
    this.firstName = participant.firstName();
    this.lastName = participant.lastName();
    this.email = participant.email();
    this.organization = participant.organization();
    this.studyInstitution = participant.studyInstitution();
    this.studyProgramme = participant.studyProgramme();
    this.studentId = participant.studentId();
    this.personalDataConsentAt = personalDataConsentAt;
    this.submittedAt = submittedAt;
    this.options = new LinkedHashSet<>(options);
  }

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

  public Instant getPersonalDataConsentAt() {
    return personalDataConsentAt;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  /** Selected options in display order. */
  public List<ConferenceOption> getOptions() {
    List<ConferenceOption> sorted = new ArrayList<>(options);
    sorted.sort(OPTION_ORDER);
    return sorted;
  }
}
