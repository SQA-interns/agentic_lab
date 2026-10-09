package si.konferenca.registration.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Locale;
import si.konferenca.registration.application.DuplicateEmailException;
import si.konferenca.registration.application.RegistrationRepository;
import si.konferenca.registration.domain.Registration;

/** {@link RegistrationRepository} on JPA; queries are parameterised (SB-05). */
public class JpaRegistrationRepository implements RegistrationRepository {

  static final String EMAIL_CONSTRAINT = "uq_registration_email";

  private final EntityManager entityManager;

  public JpaRegistrationRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public boolean existsByEmailNormalized(String emailNormalized) {
    Long count =
        entityManager
            .createQuery(
                "select count(r) from Registration r where r.emailNormalized = :email", Long.class)
            .setParameter("email", emailNormalized)
            .getSingleResult();
    return count > 0;
  }

  @Override
  public void insert(Registration registration) {
    try {
      entityManager.persist(registration);
      entityManager.flush();
    } catch (PersistenceException e) {
      if (violates(e, EMAIL_CONSTRAINT)) {
        throw new DuplicateEmailException();
      }
      throw e;
    }
  }

  /**
   * Loads options and consents with two fetch joins in the current transaction (two bag collections
   * cannot be fetched in one query).
   */
  @Override
  public List<Registration> findAllOldestFirst() {
    List<Registration> registrations =
        entityManager
            .createQuery(
                "select distinct r from Registration r left join fetch r.options"
                    + " order by r.registeredAt, r.id",
                Registration.class)
            .getResultList();
    if (!registrations.isEmpty()) {
      entityManager
          .createQuery(
              "select distinct r from Registration r left join fetch r.consents where r in :all",
              Registration.class)
          .setParameter("all", registrations)
          .getResultList();
    }
    return registrations;
  }

  private static boolean violates(Throwable failure, String constraint) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
      if (t instanceof org.hibernate.exception.ConstraintViolationException violation) {
        String name = violation.getConstraintName();
        if (name != null && name.toLowerCase(Locale.ROOT).contains(constraint)) {
          return true;
        }
      }
    }
    return false;
  }
}
