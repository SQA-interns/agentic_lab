package org.conference.registration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.conference.registration.config.AppProperties;
import org.conference.registration.domain.ConferenceOption;
import org.conference.registration.domain.OptionCategory;
import org.conference.registration.domain.RegistrationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * Configurable workshops, events, meals and other activities (US-003), loaded once at startup from
 * the options file. An invalid file aborts startup.
 */
@Service
public final class OptionCatalog {

  static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
  private static final int MAX_NAME_LENGTH = 200;

  private final Map<String, ConferenceOption> optionsById;

  @Autowired
  public OptionCatalog(
      AppProperties properties, ResourceLoader resourceLoader, ObjectMapper mapper) {
    this(load(resourceLoader.getResource(properties.options().file()), mapper));
  }

  OptionCatalog(List<OptionDefinition> definitions) {
    this.optionsById = validate(definitions);
  }

  /** Active options offered to the given registration variant, in configuration order. */
  public List<ConferenceOption> activeFor(RegistrationType type) {
    return optionsById.values().stream().filter(option -> option.availableTo(type)).toList();
  }

  /**
   * Resolves submitted identifiers; unknown, inactive, duplicate or not-for-this-variant
   * identifiers are rejected.
   */
  public List<ConferenceOption> resolve(RegistrationType type, List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    Set<String> seen = new HashSet<>();
    List<ConferenceOption> resolved = new ArrayList<>();
    for (String id : ids) {
      if (!seen.add(id)) {
        throw new RegistrationValidationException("optionIds", "Options must not be repeated.");
      }
      ConferenceOption option = optionsById.get(id);
      if (option == null || !option.availableTo(type)) {
        throw new RegistrationValidationException(
            "optionIds", "One or more selected options are not available.");
      }
      resolved.add(option);
    }
    return resolved;
  }

  private static List<OptionDefinition> load(Resource resource, ObjectMapper mapper) {
    try (InputStream in = resource.getInputStream()) {
      OptionsFile file = mapper.readValue(in, OptionsFile.class);
      if (file == null || file.options() == null) {
        throw new IllegalStateException("Options file must contain an 'options' array");
      }
      return file.options();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read conference options file " + resource, e);
    }
  }

  private static Map<String, ConferenceOption> validate(List<OptionDefinition> definitions) {
    Map<String, ConferenceOption> result = new LinkedHashMap<>();
    for (OptionDefinition def : definitions) {
      if (def == null || def.id() == null || !ID_PATTERN.matcher(def.id()).matches()) {
        throw new IllegalStateException(
            "Invalid option id " + (def == null ? null : def.id()) + ": must match " + ID_PATTERN);
      }
      if (def.name() == null || def.name().isBlank() || def.name().length() > MAX_NAME_LENGTH) {
        throw new IllegalStateException("Option " + def.id() + " needs a name of 1-200 characters");
      }
      if (def.category() == null) {
        throw new IllegalStateException("Option " + def.id() + " needs a category");
      }
      if (def.active() == null) {
        throw new IllegalStateException("Option " + def.id() + " needs an explicit active flag");
      }
      Set<RegistrationType> audiences =
          def.audiences() == null || def.audiences().isEmpty()
              ? EnumSet.allOf(RegistrationType.class)
              : EnumSet.copyOf(def.audiences());
      ConferenceOption option =
          new ConferenceOption(def.id(), def.category(), def.name(), def.active(), audiences);
      if (result.putIfAbsent(def.id(), option) != null) {
        throw new IllegalStateException("Duplicate option id " + def.id());
      }
    }
    return result;
  }

  /** Root of the options JSON file. */
  record OptionsFile(List<OptionDefinition> options) {}

  /** One option entry as written by organizers; {@code audiences} defaults to both variants. */
  record OptionDefinition(
      String id,
      OptionCategory category,
      String name,
      Boolean active,
      Set<RegistrationType> audiences) {}
}
