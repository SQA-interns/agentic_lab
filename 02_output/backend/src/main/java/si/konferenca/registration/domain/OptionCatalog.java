package si.konferenca.registration.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The configured consent and options; identifiers are unique (AC-003-04). */
public final class OptionCatalog {

  private final Consent consent;
  private final List<ConferenceOption> options;

  public OptionCatalog(Consent consent, List<ConferenceOption> options) {
    Set<String> ids = new HashSet<>();
    for (ConferenceOption o : options) {
      if (!ids.add(o.id())) {
        throw new IllegalArgumentException("Duplicate option identifier '" + o.id() + "'");
      }
    }
    this.consent = consent;
    this.options = List.copyOf(options);
  }

  public Consent consent() {
    return consent;
  }

  public Optional<ConferenceOption> find(String id) {
    return options.stream().filter(o -> o.id().equals(id)).findFirst();
  }

  /** Active options in configuration order (BR-04). */
  public List<ConferenceOption> activeOptions() {
    return options.stream().filter(ConferenceOption::active).toList();
  }
}
