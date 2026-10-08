package si.konferenca.registration.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Persistable;
import si.konferenca.registration.domain.EmailAddress;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;

/** Row of {@code registration} (database.sql) with its options and consents. */
@Entity
@Table(name = "registration")
public class RegistrationEntity implements Persistable<UUID> {

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

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @ElementCollection
  @CollectionTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"))
  private List<OptionEntry> options = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "registration_consent",
      joinColumns = @JoinColumn(name = "registration_id"))
  private List<ConsentEntry> consents = new ArrayList<>();

  @Transient private boolean fresh = true;

  protected RegistrationEntity() {}

  static RegistrationEntity from(Registration r) {
    RegistrationEntity e = new RegistrationEntity();
    Participant p = r.participant();
    e.id = r.id();
    e.type = r.type();
    e.firstName = p.firstName();
    e.lastName = p.lastName();
    e.email = p.email();
    e.emailNormalized = EmailAddress.normalise(p.email());
    e.organization = p.organization();
    e.studyInstitution = p.studyInstitution();
    e.studyProgramme = p.studyProgramme();
    e.studentId = p.studentId();
    e.receivedAt = r.receivedAt();
    r.options().forEach(o -> e.options.add(new OptionEntry(o.id(), o.name(), o.category())));
    r.consents().forEach(c -> e.consents.add(new ConsentEntry(c.id(), c.text(), c.givenAt())));
    return e;
  }

  Registration toDomain() {
    return new Registration(
        id,
        receivedAt,
        type,
        new Participant(
            firstName, lastName, email, organization, studyInstitution, studyProgramme, studentId),
        options.stream()
            .map(o -> new SelectedOption(o.optionId(), o.optionName(), o.category()))
            .toList(),
        consents.stream()
            .map(c -> new GivenConsent(c.consentId(), c.consentText(), c.givenAt()))
            .toList());
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return fresh;
  }

  @PostLoad
  @PostPersist
  void markStored() {
    fresh = false;
  }
}
