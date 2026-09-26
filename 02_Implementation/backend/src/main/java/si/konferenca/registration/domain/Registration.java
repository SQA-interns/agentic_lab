package si.konferenca.registration.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** An accepted conference registration. */
@Entity
@Table(name = "registration")
public class Registration {

  @Id @GeneratedValue @UuidGenerator private UUID id;

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

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"))
  private Set<SelectedOption> selectedOptions = new LinkedHashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "registration_consent",
      joinColumns = @JoinColumn(name = "registration_id"))
  @Column(name = "consent_id", nullable = false, length = 64)
  private Set<String> consentIds = new LinkedHashSet<>();

  protected Registration() {}

  private Registration(Builder builder) {
    this.type = builder.type;
    this.firstName = builder.firstName;
    this.lastName = builder.lastName;
    this.email = builder.email;
    this.organization = builder.organization;
    this.studyInstitution = builder.studyInstitution;
    this.studyProgramme = builder.studyProgramme;
    this.studentId = builder.studentId;
    this.createdAt = builder.createdAt;
    this.selectedOptions = new LinkedHashSet<>(builder.selectedOptions);
    this.consentIds = new LinkedHashSet<>(builder.consentIds);
  }

  public static Builder builder() {
    return new Builder();
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Selected options ordered by category, then display name. */
  public List<SelectedOption> getSelectedOptions() {
    return selectedOptions.stream()
        .sorted(
            Comparator.comparing(SelectedOption::getCategory)
                .thenComparing(SelectedOption::getOptionName))
        .toList();
  }

  public List<SelectedOption> getSelectedOptions(OptionCategory category) {
    return getSelectedOptions().stream().filter(o -> o.getCategory() == category).toList();
  }

  /** Given consent identifiers in alphabetical order. */
  public List<String> getConsentIds() {
    return consentIds.stream().sorted().toList();
  }

  /** Builder for new registrations; the identifier is assigned on persist. */
  public static final class Builder {
    private RegistrationType type;
    private String firstName;
    private String lastName;
    private String email;
    private String organization;
    private String studyInstitution;
    private String studyProgramme;
    private String studentId;
    private Instant createdAt;
    private Collection<SelectedOption> selectedOptions = List.of();
    private Collection<String> consentIds = List.of();

    private Builder() {}

    public Builder type(RegistrationType value) {
      this.type = value;
      return this;
    }

    public Builder firstName(String value) {
      this.firstName = value;
      return this;
    }

    public Builder lastName(String value) {
      this.lastName = value;
      return this;
    }

    public Builder email(String value) {
      this.email = value;
      return this;
    }

    public Builder organization(String value) {
      this.organization = value;
      return this;
    }

    public Builder studyInstitution(String value) {
      this.studyInstitution = value;
      return this;
    }

    public Builder studyProgramme(String value) {
      this.studyProgramme = value;
      return this;
    }

    public Builder studentId(String value) {
      this.studentId = value;
      return this;
    }

    public Builder createdAt(Instant value) {
      this.createdAt = value;
      return this;
    }

    public Builder selectedOptions(Collection<SelectedOption> value) {
      this.selectedOptions = List.copyOf(value);
      return this;
    }

    public Builder consentIds(Collection<String> value) {
      this.consentIds = List.copyOf(value);
      return this;
    }

    public Registration build() {
      return new Registration(this);
    }
  }
}
