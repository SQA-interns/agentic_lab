package org.conference.registration.service;

import java.util.List;
import org.conference.registration.domain.ParticipantDetails;
import org.conference.registration.domain.RegistrationType;

/** A validated registration request handed from the API layer to the service. */
public record RegistrationCommand(
    RegistrationType type,
    ParticipantDetails participant,
    boolean privacyConsent,
    List<String> optionIds,
    String formToken,
    String honeypot) {

  public RegistrationCommand {
    optionIds = optionIds == null ? List.of() : List.copyOf(optionIds);
  }
}
