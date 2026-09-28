package si.konferenca.registration.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for registrations. */
public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

  List<Registration> findAllByOrderByCreatedAtAsc();
}
