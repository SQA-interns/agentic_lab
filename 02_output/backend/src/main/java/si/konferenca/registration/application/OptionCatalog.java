package si.konferenca.registration.application;

import java.util.List;
import java.util.Optional;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;

/** Port: the configured conference options and consents (AR-04). */
public interface OptionCatalog {

  /** Active options in configuration order. */
  List<ConferenceOption> activeOptions();

  /** Any configured option, active or not. */
  Optional<ConferenceOption> find(String id);

  List<ConsentDefinition> consents();
}
