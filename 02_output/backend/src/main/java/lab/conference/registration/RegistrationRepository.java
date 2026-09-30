package lab.conference.registration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Registration persistence (parameterised queries only, SB-05). */
public interface RegistrationRepository extends JpaRepository<RegistrationEntity, UUID> {

  Optional<RegistrationEntity> findByClientRequestId(UUID clientRequestId);

  @Query("SELECT r FROM RegistrationEntity r ORDER BY r.acceptedAt, r.id")
  List<RegistrationEntity> findAllInAcceptanceOrder();
}
