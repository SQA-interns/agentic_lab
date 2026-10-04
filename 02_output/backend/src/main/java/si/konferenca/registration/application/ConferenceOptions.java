package si.konferenca.registration.application;

import java.util.List;
import java.util.Optional;
import si.konferenca.registration.domain.OptionCategory;

/** The configured options and consent (conference-options.schema.json), read once at start. */
public record ConferenceOptions(Consent consent, List<Option> options) {

  /** The single mandatory consent (D-07). */
  public record Consent(String id, String text) {}

  /** One selectable option; the id is stable across renames (AC-003-04). */
  public record Option(String id, String name, OptionCategory category, boolean active) {}

  public ConferenceOptions {
    options = List.copyOf(options);
  }

  public Optional<Option> find(String id) {
    return options.stream().filter(o -> o.id().equals(id)).findFirst();
  }

  public List<Option> active() {
    return options.stream().filter(Option::active).toList();
  }
}
