package lab.conference.registration;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Accepted registration (registration + registration_selection, spec section 5). */
@Entity
@Table(name = "registration")
public class RegistrationEntity {

  @Id private UUID id;

  @Column(name = "client_request_id", nullable = false, unique = true)
  private UUID clientRequestId;

  @Column(name = "request_fingerprint", nullable = false, length = 64)
  private String requestFingerprint;

  @Column(name = "form_type", nullable = false, length = 16)
  private String formType;

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

  @Column(name = "student_id", length = 64)
  private String studentId;

  @Column(name = "consent_id", length = 64)
  private String consentId;

  @Column(name = "consent_given")
  private Boolean consentGiven;

  @Column(name = "json_sha256", nullable = false, length = 64)
  private String jsonSha256;

  @Column(name = "accepted_at", nullable = false)
  private Instant acceptedAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "registration_selection",
      joinColumns = @JoinColumn(name = "registration_id"))
  @OrderColumn(name = "position")
  private List<Selection> selections = new ArrayList<>();

  protected RegistrationEntity() {}

  RegistrationEntity(UUID id, Instant acceptedAt, ValidatedRegistration r, String jsonSha256) {
    this.id = id;
    this.clientRequestId = r.clientRequestId();
    this.requestFingerprint = r.fingerprint();
    this.formType = r.formType().key();
    this.firstName = r.field("firstName");
    this.lastName = r.field("lastName");
    this.email = r.field("email");
    this.organization = r.field("organization");
    this.studyInstitution = r.field("studyInstitution");
    this.studyProgramme = r.field("studyProgramme");
    this.studentId = r.field("studentId");
    if (r.consent() != null) {
      this.consentId = r.consent().id();
      this.consentGiven = r.consent().given();
    }
    this.jsonSha256 = jsonSha256;
    this.acceptedAt = acceptedAt;
    r.selections()
        .forEach(
            (g, opts) ->
                opts.forEach(o -> selections.add(new Selection(g.key(), o.id(), o.name()))));
  }

  /** One selected option with its display name at acceptance time. */
  @Embeddable
  public static class Selection {
    @Column(name = "group_id", nullable = false, length = 16)
    private String groupId;

    @Column(name = "option_id", nullable = false, length = 64)
    private String optionId;

    @Column(name = "option_name", nullable = false, length = 200)
    private String optionName;

    protected Selection() {}

    Selection(String groupId, String optionId, String optionName) {
      this.groupId = groupId;
      this.optionId = optionId;
      this.optionName = optionName;
    }

    public String groupId() {
      return groupId;
    }

    public String optionId() {
      return optionId;
    }

    public String optionName() {
      return optionName;
    }
  }

  public UUID id() {
    return id;
  }

  public UUID clientRequestId() {
    return clientRequestId;
  }

  public String requestFingerprint() {
    return requestFingerprint;
  }

  public String formType() {
    return formType;
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

  public Boolean consentGiven() {
    return consentGiven;
  }

  public String jsonSha256() {
    return jsonSha256;
  }

  public Instant acceptedAt() {
    return acceptedAt;
  }

  public List<Selection> selections() {
    return List.copyOf(selections);
  }
}
