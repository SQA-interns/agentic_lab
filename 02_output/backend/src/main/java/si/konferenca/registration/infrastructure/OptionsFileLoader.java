package si.konferenca.registration.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.OptionCatalogue;
import si.konferenca.registration.domain.RegistrationType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the conference options file (docs/02_contracts/conference-options.schema.json) and refuses
 * an invalid one with a message naming the problem (D-16).
 */
public final class OptionsFileLoader {

  private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
  private static final int MAX_SELECTIONS = 50;
  private static final int MAX_NAME_LENGTH = 200;
  private static final int MAX_CONSENT_TEXT_LENGTH = 2000;

  private static final JsonMapper JSON =
      JsonMapper.builder()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .build();

  /** Thrown when the options file breaks its schema. */
  public static final class InvalidOptionsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    InvalidOptionsException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  record FileContent(
      Map<String, CategoryLimit> categories,
      List<OptionEntry> options,
      List<ConsentEntry> consents) {}

  record CategoryLimit(Integer maxSelections) {}

  record OptionEntry(
      String id,
      String name,
      String category,
      Boolean active,
      @JsonProperty("availableTo") List<RegistrationType> availableTo) {}

  record ConsentEntry(String id, String text, Boolean mandatory) {}

  public OptionCatalogue load(Resource resource) {
    FileContent content;
    try (InputStream in = resource.getInputStream()) {
      content = JSON.readValue(in, FileContent.class);
    } catch (JacksonException e) {
      throw invalid(resource, e.getOriginalMessage(), e);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Options file " + resource.getDescription() + " not readable", e);
    }
    return toCatalogue(resource, content);
  }

  private static OptionCatalogue toCatalogue(Resource resource, FileContent content) {
    if (content == null
        || content.categories() == null
        || content.options() == null
        || content.consents() == null) {
      throw invalid(resource, "categories, options and consents are required", null);
    }
    Map<Category, Integer> limits = new EnumMap<>(Category.class);
    content
        .categories()
        .forEach(
            (name, limit) -> {
              Category category =
                  Category.fromValue(name)
                      .orElseThrow(() -> invalid(resource, "unknown category " + name, null));
              if (limit == null
                  || limit.maxSelections() == null
                  || limit.maxSelections() < 0
                  || limit.maxSelections() > MAX_SELECTIONS) {
                throw invalid(
                    resource, "maxSelections of " + name + " must be 0.." + MAX_SELECTIONS, null);
              }
              limits.put(category, limit.maxSelections());
            });
    return new OptionCatalogue(
        limits, options(resource, content.options()), consents(resource, content.consents()));
  }

  private static List<ConferenceOption> options(Resource resource, List<OptionEntry> entries) {
    List<ConferenceOption> options = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (OptionEntry entry : entries) {
      String id = requireId(resource, "option", entry.id());
      if (!ids.add(id)) {
        throw invalid(resource, "duplicate option id " + id, null);
      }
      if (entry.name() == null
          || entry.name().isBlank()
          || entry.name().length() > MAX_NAME_LENGTH) {
        throw invalid(
            resource, "option " + id + ": name is required (1.." + MAX_NAME_LENGTH + ")", null);
      }
      Category category =
          Category.fromValue(entry.category() == null ? "" : entry.category())
              .orElseThrow(
                  () ->
                      invalid(
                          resource,
                          "option " + id + ": unknown category " + entry.category(),
                          null));
      if (entry.active() == null) {
        throw invalid(resource, "option " + id + ": active is required", null);
      }
      Set<RegistrationType> availableTo = EnumSet.allOf(RegistrationType.class);
      if (entry.availableTo() != null) {
        if (entry.availableTo().isEmpty() || entry.availableTo().contains(null)) {
          throw invalid(
              resource, "option " + id + ": availableTo must list registration types", null);
        }
        availableTo = EnumSet.copyOf(entry.availableTo());
      }
      options.add(new ConferenceOption(id, entry.name(), category, entry.active(), availableTo));
    }
    return options;
  }

  private static List<ConsentDefinition> consents(Resource resource, List<ConsentEntry> entries) {
    if (entries.isEmpty()) {
      throw invalid(resource, "at least one consent is required", null);
    }
    List<ConsentDefinition> consents = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (ConsentEntry entry : entries) {
      String id = requireId(resource, "consent", entry.id());
      if (!ids.add(id)) {
        throw invalid(resource, "duplicate consent id " + id, null);
      }
      if (entry.text() == null
          || entry.text().isBlank()
          || entry.text().length() > MAX_CONSENT_TEXT_LENGTH) {
        throw invalid(resource, "consent " + id + ": text is required", null);
      }
      if (entry.mandatory() == null) {
        throw invalid(resource, "consent " + id + ": mandatory is required", null);
      }
      consents.add(new ConsentDefinition(id, entry.text(), entry.mandatory()));
    }
    return consents;
  }

  private static String requireId(Resource resource, String kind, String id) {
    if (id == null || !ID.matcher(id).matches()) {
      throw invalid(resource, kind + " id " + id + " must match " + ID.pattern(), null);
    }
    return id;
  }

  private static InvalidOptionsException invalid(
      Resource resource, String problem, Throwable cause) {
    return new InvalidOptionsException(
        "Invalid conference options file " + resource.getDescription() + ": " + problem, cause);
  }
}
