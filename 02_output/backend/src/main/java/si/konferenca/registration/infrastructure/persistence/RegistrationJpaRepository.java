package si.konferenca.registration.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the registration table. */
public interface RegistrationJpaRepository extends JpaRepository<RegistrationEntity, UUID> {

  boolean existsByEmailNormalized(String emailNormalized);

  List<RegistrationEntity> findAllByOrderByReceivedAtAsc();
}
