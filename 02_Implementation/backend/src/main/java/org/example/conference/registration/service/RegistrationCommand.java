package org.example.conference.registration.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.example.conference.registration.api.SelectionsRequest;
import org.example.conference.registration.domain.ParticipantType;

/** Normalized submission: fixed fields in form order (already trimmed and bean-validated). */
public record RegistrationCommand(
    UUID clientRequestId,
    ParticipantType participantType,
    Map<String, String> fields,
    SelectionsRequest selections,
    Map<String, Boolean> consents,
    String captchaToken) {

  public RegistrationCommand {
    fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    consents = Map.copyOf(consents);
  }
}
