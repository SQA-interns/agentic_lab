package si.konferenca.registration.service;

import java.util.List;
import java.util.Optional;
import si.konferenca.registration.domain.ConferenceOption;

/** Port: source of the configured conference options. */
public interface ConferenceOptionCatalog {

  /** All valid configured options (active and inactive) in configuration order. */
  List<ConferenceOption> findAll();

  Optional<ConferenceOption> findById(String id);
}
