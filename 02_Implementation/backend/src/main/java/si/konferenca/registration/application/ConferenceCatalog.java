package si.konferenca.registration.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;

/**
 * Conference options and consents loaded from configuration. The configuration is validated on
 * startup so that an invalid programme definition fails fast.
 */
@Component
public class ConferenceCatalog {

  static final Pattern IDENTIFIER = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");

  private final Map<String, ConferenceOption> options;
  private final Map<String, ConsentDefinition> consents;

  public ConferenceCatalog(AppProperties properties) {
    this.options = loadOptions(properties.conference().options());
    this.consents = loadConsents(properties.conference().consents());
  }

  private static Map<String, ConferenceOption> loadOptions(
      List<AppProperties.OptionEntry> entries) {
    Map<String, ConferenceOption> result = new LinkedHashMap<>();
    for (AppProperties.OptionEntry entry : entries) {
      requireIdentifier(entry.id(), "conference option");
      requireText(entry.name(), "conference option '" + entry.id() + "' name");
      if (entry.category() == null) {
        throw new IllegalStateException(
            "Conference option '" + entry.id() + "' must define a category");
      }
      ConferenceOption option =
          new ConferenceOption(entry.id(), entry.name().strip(), entry.category(), entry.active());
      if (result.putIfAbsent(option.id(), option) != null) {
        throw new IllegalStateException("Duplicate conference option id '" + entry.id() + "'");
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private static Map<String, ConsentDefinition> loadConsents(
      List<AppProperties.ConsentEntry> entries) {
    Map<String, ConsentDefinition> result = new LinkedHashMap<>();
    for (AppProperties.ConsentEntry entry : entries) {
      requireIdentifier(entry.id(), "consent");
      requireText(entry.label(), "consent '" + entry.id() + "' label");
      ConsentDefinition consent =
          new ConsentDefinition(entry.id(), entry.label().strip(), entry.required());
      if (result.putIfAbsent(consent.id(), consent) != null) {
        throw new IllegalStateException("Duplicate consent id '" + entry.id() + "'");
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private static void requireIdentifier(String id, String what) {
    if (id == null || !IDENTIFIER.matcher(id).matches()) {
      throw new IllegalStateException(
          "Invalid " + what + " id '" + id + "': must match " + IDENTIFIER.pattern());
    }
  }

  private static void requireText(String value, String what) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing " + what);
    }
  }

  /** Active options in configuration order. */
  public List<ConferenceOption> activeOptions() {
    return options.values().stream().filter(ConferenceOption::active).toList();
  }

  public Optional<ConferenceOption> findActiveOption(String id) {
    return Optional.ofNullable(id).map(options::get).filter(ConferenceOption::active);
  }

  public List<ConsentDefinition> consents() {
    return List.copyOf(consents.values());
  }

  public boolean isKnownConsent(String id) {
    return id != null && consents.containsKey(id);
  }
}
