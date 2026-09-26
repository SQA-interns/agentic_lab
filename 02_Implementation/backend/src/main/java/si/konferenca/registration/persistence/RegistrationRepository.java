package si.konferenca.registration.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import si.konferenca.registration.domain.Registration;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

  List<Registration> findAllByOrderByCreatedAtAsc();
}
