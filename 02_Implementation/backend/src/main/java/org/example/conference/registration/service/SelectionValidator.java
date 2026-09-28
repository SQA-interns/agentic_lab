package org.example.conference.registration.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.example.conference.catalog.Catalog;
import org.example.conference.catalog.CatalogOption;
import org.example.conference.catalog.ConsentDefinition;
import org.example.conference.catalog.OptionGroup;
import org.example.conference.registration.api.SelectionsRequest;
import org.example.conference.shared.api.ApiException;
import org.example.conference.shared.api.ErrorCode;
import org.example.conference.shared.api.FieldError;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Server-side enforcement of the startup catalog (AR-07): only known, active option IDs of the
 * matching group, each at most once; required consents must be granted; unknown consents rejected.
 */
@Component
public class SelectionValidator {

  private final Catalog catalog;

  public SelectionValidator(Catalog catalog) {
    this.catalog = catalog;
  }

  public ValidatedRegistration validate(RegistrationCommand command) {
    List<FieldError> optionErrors = new ArrayList<>();
    Map<OptionGroup, List<CatalogOption>> resolved = new EnumMap<>(OptionGroup.class);
    for (OptionGroup group : OptionGroup.values()) {
      List<String> ids = idsFor(command.selections(), group);
      Set<String> seen = new HashSet<>();
      for (int i = 0; i < ids.size(); i++) {
        String id = ids.get(i);
        Optional<CatalogOption> option = catalog.findActive(group, id);
        if (option.isEmpty() || !seen.add(id)) {
          optionErrors.add(
              new FieldError("selections." + group.key() + "[" + i + "]", "OPTION_INVALID"));
        }
      }
      resolved.put(
          group, catalog.activeOptions(group).stream().filter(o -> seen.contains(o.id())).toList());
    }
    if (!optionErrors.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.OPTION_INVALID, optionErrors);
    }
    return new ValidatedRegistration(command, resolved, validateConsents(command.consents()));
  }

  private List<ConsentDefinition> validateConsents(Map<String, Boolean> consents) {
    List<FieldError> unknown = new ArrayList<>();
    for (String id : consents.keySet()) {
      if (catalog.findConsent(id).isEmpty()) {
        unknown.add(new FieldError("consents." + id, "CONSENT_INVALID"));
      }
    }
    if (!unknown.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.CONSENT_INVALID, unknown);
    }
    List<FieldError> missing = new ArrayList<>();
    List<ConsentDefinition> granted = new ArrayList<>();
    for (ConsentDefinition consent : catalog.consents()) {
      boolean given = Boolean.TRUE.equals(consents.get(consent.id()));
      if (given) {
        granted.add(consent);
      } else if (consent.required()) {
        missing.add(new FieldError("consents." + consent.id(), "CONSENT_REQUIRED"));
      }
    }
    if (!missing.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.CONSENT_REQUIRED, missing);
    }
    return granted;
  }

  private static List<String> idsFor(SelectionsRequest selections, OptionGroup group) {
    List<String> ids =
        switch (group) {
          case WORKSHOPS -> selections.workshops();
          case EVENTS -> selections.events();
          case MEALS -> selections.meals();
          case OTHER_ACTIVITIES -> selections.otherActivities();
        };
    return ids == null ? List.of() : ids;
  }
}
