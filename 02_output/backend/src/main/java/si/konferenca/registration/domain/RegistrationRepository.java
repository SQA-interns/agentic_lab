package si.konferenca.registration.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Stored registrations; queries are parameterised by Spring Data (SB-05). */
public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

  boolean existsByEmailNormalized(String emailNormalized);

  /** Every registration with its options, oldest first (export order). */
  @EntityGraph(attributePaths = "options")
  List<Registration> findAllByOrderByAcceptedAtAscIdAsc();
}
