package si.konferenca.registration.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Row of table registration (docs/02_contracts/db-schema.sql) with its options and consents. */
@Entity
@Table(name = "registration")
class RegistrationEntity {

  @Id UUID id;

  @Column(nullable = false, length = 16)
  String type;

  @Column(name = "first_name", nullable = false, length = 100)
  String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  String lastName;

  @Column(nullable = false, length = 254)
  String email;

  @Column(name = "email_normalized", nullable = false, length = 254)
  String emailNormalized;

  @Column(length = 200)
  String organization;

  @Column(name = "study_institution", length = 200)
  String studyInstitution;

  @Column(name = "study_programme", length = 200)
  String studyProgramme;

  @Column(name = "student_id", length = 50)
  String studentId;

  @Column(name = "received_at", nullable = false)
  Instant receivedAt;

  @ElementCollection
  @CollectionTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"))
  List<OptionRow> options = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "registration_consent",
      joinColumns = @JoinColumn(name = "registration_id"))
  List<ConsentRow> consents = new ArrayList<>();

  /** Row of table registration_option. */
  @Embeddable
  static class OptionRow {
    @Column(name = "option_id", nullable = false, length = 64)
    String optionId;

    @Column(name = "option_name", nullable = false, length = 200)
    String optionName;

    @Column(nullable = false, length = 16)
    String category;
  }

  /** Row of table registration_consent. */
  @Embeddable
  static class ConsentRow {
    @Column(name = "consent_id", nullable = false, length = 64)
    String consentId;

    @Column(name = "consent_text", nullable = false, length = 2000)
    String consentText;

    @Column(name = "given_at", nullable = false)
    Instant givenAt;
  }
}
