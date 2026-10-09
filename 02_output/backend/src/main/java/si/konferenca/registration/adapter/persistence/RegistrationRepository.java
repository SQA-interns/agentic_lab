package si.konferenca.registration.adapter.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository of {@link RegistrationEntity}. */
public interface RegistrationRepository extends JpaRepository<RegistrationEntity, UUID> {

  boolean existsByEmailNormalized(String emailNormalized);

  List<RegistrationEntity> findAllByOrderBySubmittedAtAsc();
}
