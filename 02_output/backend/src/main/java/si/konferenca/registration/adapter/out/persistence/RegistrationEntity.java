package si.konferenca.registration.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

/** Row of the table {@code registration} (registration-schema.sql). */
@Entity
@Table(name = "registration")
class RegistrationEntity {

  @Id
  @Column(name = "id")
  UUID id;

  @Column(name = "type")
  String type;

  @Column(name = "first_name")
  String firstName;

  @Column(name = "last_name")
  String lastName;

  @Column(name = "email")
  String email;

  @Column(name = "organization")
  String organization;

  @Column(name = "study_institution")
  String studyInstitution;

  @Column(name = "study_programme")
  String studyProgramme;

  @Column(name = "student_id")
  String studentId;

  @Column(name = "consent_id")
  String consentId;

  @Column(name = "consent_text")
  String consentText;

  @Column(name = "consent_given_at")
  Instant consentGivenAt;

  @Column(name = "accepted_at")
  Instant acceptedAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "registration_option",
      joinColumns = @JoinColumn(name = "registration_id"))
  @OrderColumn(name = "position")
  List<RegistrationOptionEmbeddable> options = new ArrayList<>();
}
