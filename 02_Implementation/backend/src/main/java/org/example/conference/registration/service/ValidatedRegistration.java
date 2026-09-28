package org.example.conference.registration.service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.example.conference.catalog.CatalogOption;
import org.example.conference.catalog.ConsentDefinition;
import org.example.conference.catalog.OptionGroup;

/** Command after catalog validation: resolved options (catalog order) and granted consents. */
public record ValidatedRegistration(
    RegistrationCommand command,
    Map<OptionGroup, List<CatalogOption>> selections,
    List<ConsentDefinition> grantedConsents) {

  public ValidatedRegistration {
    selections = Collections.unmodifiableMap(new EnumMap<>(selections));
    grantedConsents = List.copyOf(grantedConsents);
  }
}
