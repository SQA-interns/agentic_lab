package si.konferenca.registration.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.Registration.Consent;
import si.konferenca.registration.domain.Registration.SelectedOption;
import si.konferenca.registration.domain.RegistrationStore;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.TextField;

/** The registration database, through JPA with parameterised statements only (SB-05). */
public class JpaRegistrationStore implements RegistrationStore {

  @PersistenceContext private EntityManager entityManager;

  @Override
  public void insert(Registration registration) {
    entityManager.persist(toEntity(registration));
    // Written now, so a database failure is known before the JSON copy is made (AR-05).
    entityManager.flush();
  }

  @Override
  public List<Registration> findAll() {
    return entityManager
        .createQuery(
            "select r from RegistrationEntity r order by r.acceptedAt, r.id",
            RegistrationEntity.class)
        .getResultList()
        .stream()
        .map(JpaRegistrationStore::toDomain)
        .toList();
  }

  private static RegistrationEntity toEntity(Registration registration) {
    RegistrationEntity entity = new RegistrationEntity();
    entity.id = registration.id();
    entity.type = registration.type().name();
    entity.firstName = registration.value(TextField.FIRST_NAME);
    entity.lastName = registration.value(TextField.LAST_NAME);
    entity.email = registration.value(TextField.EMAIL);
    entity.organization = registration.value(TextField.ORGANIZATION);
    entity.studyInstitution = registration.value(TextField.STUDY_INSTITUTION);
    entity.studyProgramme = registration.value(TextField.STUDY_PROGRAMME);
    entity.studentId = registration.value(TextField.STUDENT_ID);
    entity.consentId = registration.consent().id();
    entity.consentText = registration.consent().text();
    entity.consentGivenAt = registration.consent().givenAt();
    entity.acceptedAt = registration.acceptedAt();
    for (SelectedOption option : registration.options()) {
      entity.options.add(
          new RegistrationOptionEmbeddable(option.id(), option.name(), option.category().code()));
    }
    return entity;
  }

  private static Registration toDomain(RegistrationEntity entity) {
    Map<TextField, String> values = new EnumMap<>(TextField.class);
    put(values, TextField.FIRST_NAME, entity.firstName);
    put(values, TextField.LAST_NAME, entity.lastName);
    put(values, TextField.EMAIL, entity.email);
    put(values, TextField.ORGANIZATION, entity.organization);
    put(values, TextField.STUDY_INSTITUTION, entity.studyInstitution);
    put(values, TextField.STUDY_PROGRAMME, entity.studyProgramme);
    put(values, TextField.STUDENT_ID, entity.studentId);
    List<SelectedOption> options =
        entity.options.stream()
            .map(
                option ->
                    new SelectedOption(
                        option.optionId,
                        option.optionName,
                        OptionCategory.fromCode(option.optionCategory).orElseThrow()))
            .toList();
    return new Registration(
        entity.id,
        RegistrationType.valueOf(entity.type),
        entity.acceptedAt,
        values,
        options,
        new Consent(entity.consentId, entity.consentText, entity.consentGivenAt));
  }

  private static void put(Map<TextField, String> values, TextField field, String value) {
    if (value != null) {
      values.put(field, value);
    }
  }
}
