package si.konferenca.registration.domain;

import java.util.List;
import java.util.Optional;

/** Port: the configured conference options. */
public interface OptionsCatalogue {

  /** The active options, in configuration order. */
  List<ConferenceOption> activeOptions();

  /** The active option with the given identifier, if there is one (SR-04). */
  Optional<ConferenceOption> findActive(String id);
}
