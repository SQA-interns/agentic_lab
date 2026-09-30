package si.konferenca.registration.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Stored registrations (02_contracts/database-schema.sql). */
public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

  boolean existsByEmailIgnoreCase(String email);

  List<Registration> findAllByOrderBySubmittedAtAsc();
}
