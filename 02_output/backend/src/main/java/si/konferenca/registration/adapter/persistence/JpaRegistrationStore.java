package si.konferenca.registration.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;
import si.konferenca.registration.application.DuplicateEmailException;
import si.konferenca.registration.application.RegistrationStore;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Field;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

/** {@link RegistrationStore} on PostgreSQL through JPA (parameterised queries only, SB-05). */
@Repository
public class JpaRegistrationStore implements RegistrationStore {

  private static final String UNIQUE_EMAIL = "email_normalized";

  private final RegistrationRepository repository;
  private final EntityManager entityManager;

  public JpaRegistrationStore(RegistrationRepository repository, EntityManager entityManager) {
    this.repository = repository;
    this.entityManager = entityManager;
  }

  @Override
  public boolean existsByNormalizedEmail(String normalizedEmail) {
    return repository.existsByEmailNormalized(normalizedEmail);
  }

  @Override
  public void insert(Registration registration) {
    RegistrationEntity entity =
        new RegistrationEntity(
            registration.id(),
            registration.type().name(),
            registration.value(Field.FIRST_NAME),
            registration.value(Field.LAST_NAME),
            registration.email(),
            registration.normalizedEmail(),
            registration.submittedAt());
    entity.setTypeFields(
        registration.value(Field.ORGANIZATION),
        registration.value(Field.STUDY_INSTITUTION),
        registration.value(Field.STUDY_PROGRAMME),
        registration.value(Field.STUDENT_ID));
    registration
        .selectedOptions()
        .forEach(
            o -> entity.options().add(new OptionColumns(o.id(), o.name(), o.category().name())));
    registration
        .consents()
        .forEach(c -> entity.consents().add(new ConsentColumns(c.id(), c.text(), c.givenAt())));
    try {
      entityManager.persist(entity);
      entityManager.flush();
    } catch (PersistenceException e) {
      if (isUniqueEmailViolation(e)) {
        throw new DuplicateEmailException(e);
      }
      throw e;
    }
  }

  @Override
  public List<Registration> findAll() {
    return repository.findAllByOrderBySubmittedAtAsc().stream()
        .map(JpaRegistrationStore::toDomain)
        .toList();
  }

  private static Registration toDomain(RegistrationEntity entity) {
    RegistrationType type = RegistrationType.valueOf(entity.type());
    Map<Field, String> values = new EnumMap<>(Field.class);
    values.put(Field.FIRST_NAME, entity.firstName());
    values.put(Field.LAST_NAME, entity.lastName());
    values.put(Field.EMAIL, entity.email());
    putIfPresent(values, Field.ORGANIZATION, entity.organization());
    putIfPresent(values, Field.STUDY_INSTITUTION, entity.studyInstitution());
    putIfPresent(values, Field.STUDY_PROGRAMME, entity.studyProgramme());
    putIfPresent(values, Field.STUDENT_ID, entity.studentId());
    List<ConferenceOption> options =
        entity.options().stream()
            .map(
                o ->
                    new ConferenceOption(
                        o.optionId(),
                        o.optionName(),
                        Category.valueOf(o.category()),
                        true,
                        new HashSet<>(List.of(type))))
            .toList();
    List<GivenConsent> consents =
        entity.consents().stream()
            .map(c -> new GivenConsent(c.consentId(), c.consentText(), c.givenAt()))
            .toList();
    return new Registration(entity.id(), type, values, options, consents, entity.submittedAt());
  }

  private static boolean isUniqueEmailViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof ConstraintViolationException violation) {
        String name = violation.getConstraintName();
        return name != null && name.contains(UNIQUE_EMAIL);
      }
    }
    return false;
  }

  private static void putIfPresent(Map<Field, String> values, Field field, String value) {
    if (value != null) {
      values.put(field, value);
    }
  }
}
