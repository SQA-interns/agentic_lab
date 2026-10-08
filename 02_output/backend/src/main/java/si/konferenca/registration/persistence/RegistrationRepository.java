package si.konferenca.registration.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code registration}; used only through {@link RegistrationStore}. */
interface RegistrationRepository extends JpaRepository<RegistrationEntity, UUID> {

  boolean existsByEmailNormalized(String emailNormalized);

  List<RegistrationEntity> findAllByOrderByReceivedAtAsc();

  @Query("select r.id from RegistrationEntity r where r.receivedAt < :cutoff")
  List<UUID> findIdsReceivedBefore(@Param("cutoff") Instant cutoff);
}
