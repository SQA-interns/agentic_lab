package si.konferenca.registration.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import si.konferenca.registration.domain.ConferenceOption;

public interface ConferenceOptionRepository extends JpaRepository<ConferenceOption, String> {

  List<ConferenceOption> findByActiveTrue();
}
