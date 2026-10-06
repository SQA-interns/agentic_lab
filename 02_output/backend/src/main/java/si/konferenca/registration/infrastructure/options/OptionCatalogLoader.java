package si.konferenca.registration.infrastructure.options;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Consent;
import si.konferenca.registration.domain.OptionCatalog;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.RegistrationType;

/**
 * Loads the option configuration file (conference-options.schema.json, AR-04). Any violation stops
 * the startup with a message naming the offending value (AC-003-04).
 */
public final class OptionCatalogLoader {

  private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

  private OptionCatalogLoader() {}

  /** The file's structure, bound from YAML. */
  public record OptionsFile(ConsentEntry consent, List<OptionEntry> options) {}

  /** The consent entry. */
  public record ConsentEntry(String id, String text) {}

  /** One option entry. */
  public record OptionEntry(
      String id,
      String name,
      OptionCategory category,
      Boolean active,
      List<RegistrationType> offeredTo) {}

  public static OptionCatalog load(ResourceLoader loader, String location) {
    Resource resource = loader.getResource(location);
    if (!resource.exists()) {
      throw new IllegalStateException("Option configuration not found: " + location);
    }
    List<PropertySource<?>> sources;
    try {
      sources = new YamlPropertySourceLoader().load("conference-options", resource);
    } catch (IOException e) {
      throw new IllegalStateException("Option configuration cannot be read: " + location, e);
    }
    OptionsFile file =
        new Binder(ConfigurationPropertySources.from(sources))
            .bind("conference", OptionsFile.class)
            .orElseThrow(() -> new IllegalStateException("No 'conference' section in " + location));
    return toCatalog(file, location);
  }

  static OptionCatalog toCatalog(OptionsFile file, String location) {
    ConsentEntry c = file.consent();
    if (c == null || c.id() == null || !ID.matcher(c.id()).matches() || isBlank(c.text())) {
      throw invalid(location, "the consent needs an id and a text");
    }
    List<ConferenceOption> options = new ArrayList<>();
    List<OptionEntry> entries = file.options() == null ? List.of() : file.options();
    for (OptionEntry o : entries) {
      if (o.id() == null || !ID.matcher(o.id()).matches()) {
        throw invalid(location, "option id '" + o.id() + "' is missing or invalid");
      }
      if (isBlank(o.name())) {
        throw invalid(location, "option '" + o.id() + "' has no name");
      }
      if (o.category() == null) {
        throw invalid(location, "option '" + o.id() + "' has no category");
      }
      if (o.active() == null) {
        throw invalid(location, "option '" + o.id() + "' does not state active");
      }
      EnumSet<RegistrationType> offeredTo =
          o.offeredTo() == null || o.offeredTo().isEmpty()
              ? EnumSet.allOf(RegistrationType.class)
              : EnumSet.copyOf(o.offeredTo());
      options.add(
          new ConferenceOption(o.id(), o.name().strip(), o.category(), o.active(), offeredTo));
    }
    try {
      return new OptionCatalog(new Consent(c.id(), c.text().strip()), options);
    } catch (IllegalArgumentException e) {
      throw invalid(location, e.getMessage());
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static IllegalStateException invalid(String location, String reason) {
    return new IllegalStateException("Invalid option configuration " + location + ": " + reason);
  }
}
