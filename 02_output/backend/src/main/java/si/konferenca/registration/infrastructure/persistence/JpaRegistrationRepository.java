package si.konferenca.registration.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.application.RegistrationPorts.DuplicateEmailException;
import si.konferenca.registration.application.RegistrationPorts.RegistrationRepository;
import si.konferenca.registration.domain.EmailAddress;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

/** Registration storage with JPA; every query uses bound parameters (SB-05). */
public class JpaRegistrationRepository implements RegistrationRepository {

  private static final String EMAIL_UNIQUE = "registration_email_unique";

  private final EntityManager entityManager;
  private final TransactionTemplate readOnly;

  public JpaRegistrationRepository(EntityManager entityManager, TransactionTemplate readOnly) {
    this.entityManager = entityManager;
    this.readOnly = readOnly;
  }

  @Override
  public boolean existsByNormalizedEmail(String normalizedEmail) {
    Long count =
        readOnly.execute(
            status ->
                entityManager
                    .createQuery(
                        "select count(r) from RegistrationEntity r"
                            + " where r.emailNormalized = :email",
                        Long.class)
                    .setParameter("email", normalizedEmail)
                    .getSingleResult());
    return count != null && count > 0;
  }

  @Override
  public void insert(Registration registration) {
    try {
      entityManager.persist(toEntity(registration));
      entityManager.flush();
    } catch (PersistenceException e) {
      if (isEmailConflict(e)) {
        throw new DuplicateEmailException();
      }
      throw e;
    }
  }

  private static boolean isEmailConflict(Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (t instanceof ConstraintViolationException c
          && EMAIL_UNIQUE.equalsIgnoreCase(c.getConstraintName())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<Registration> findAll() {
    return readOnly.execute(
        status ->
            entityManager
                .createQuery(
                    "select r from RegistrationEntity r order by r.receivedAt, r.id",
                    RegistrationEntity.class)
                .getResultList()
                .stream()
                .map(JpaRegistrationRepository::toDomain)
                .toList());
  }

  private static RegistrationEntity toEntity(Registration r) {
    RegistrationEntity e = new RegistrationEntity();
    Participant p = r.participant();
    e.id = r.id();
    e.type = r.type().value();
    e.firstName = p.firstName();
    e.lastName = p.lastName();
    e.email = p.email();
    e.emailNormalized = EmailAddress.normalize(p.email());
    e.organization = p.organization();
    e.studyInstitution = p.studyInstitution();
    e.studyProgramme = p.studyProgramme();
    e.studentId = p.studentId();
    e.receivedAt = r.receivedAt();
    for (Registration.SelectedOption o : r.options()) {
      RegistrationEntity.OptionRow row = new RegistrationEntity.OptionRow();
      row.optionId = o.id();
      row.optionName = o.name();
      row.category = o.category().value();
      e.options.add(row);
    }
    for (Registration.GivenConsent c : r.consents()) {
      RegistrationEntity.ConsentRow row = new RegistrationEntity.ConsentRow();
      row.consentId = c.id();
      row.consentText = c.text();
      row.givenAt = c.givenAt();
      e.consents.add(row);
    }
    return e;
  }

  private static Registration toDomain(RegistrationEntity e) {
    return new Registration(
        e.id,
        RegistrationType.fromValue(e.type).orElseThrow(),
        new Participant(
            e.firstName,
            e.lastName,
            e.email,
            e.organization,
            e.studyInstitution,
            e.studyProgramme,
            e.studentId),
        e.options.stream()
            .map(
                o ->
                    new Registration.SelectedOption(
                        o.optionId,
                        o.optionName,
                        OptionCategory.fromValue(o.category).orElseThrow()))
            .toList(),
        e.consents.stream()
            .map(c -> new Registration.GivenConsent(c.consentId, c.consentText, c.givenAt))
            .toList(),
        e.receivedAt);
  }
}
