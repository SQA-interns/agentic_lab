package si.konferenca.registration.infrastructure.persistence;

import java.util.EnumSet;
import java.util.List;
import si.konferenca.registration.application.RegistrationStore;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Consent;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

/** RegistrationStore on PostgreSQL through JPA (AR-06: schema from Flyway only). */
public class JpaRegistrationStore implements RegistrationStore {

  private final RegistrationJpaRepository repository;

  public JpaRegistrationStore(RegistrationJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public boolean emailExists(String normalizedEmail) {
    return repository.existsByEmailNormalized(normalizedEmail);
  }

  @Override
  public void insert(Registration r, String jsonCopyFile) {
    repository.saveAndFlush(RegistrationEntity.from(r, jsonCopyFile));
  }

  @Override
  public List<Registration> findAll() {
    return repository.findAllByOrderByReceivedAtAsc().stream().map(this::toDomain).toList();
  }

  private Registration toDomain(RegistrationEntity e) {
    List<ConferenceOption> options =
        e.options().stream()
            .map(
                o ->
                    new ConferenceOption(
                        o.optionId(),
                        o.optionName(),
                        OptionCategory.valueOf(o.optionCategory()),
                        true,
                        EnumSet.noneOf(RegistrationType.class)))
            .toList();
    return new Registration(
        e.id(),
        RegistrationType.valueOf(e.registrationType()),
        e.firstName(),
        e.lastName(),
        e.email(),
        e.organization(),
        e.studyInstitution(),
        e.studyProgramme(),
        e.studentId(),
        options,
        new Consent(e.consentId(), e.consentText()),
        e.receivedAt());
  }
}
