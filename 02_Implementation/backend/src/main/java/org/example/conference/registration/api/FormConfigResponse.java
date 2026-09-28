package org.example.conference.registration.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runtime form configuration for the frontend (active options only). */
public record FormConfigResponse(
    String conferenceName,
    Map<String, List<Option>> optionGroups,
    List<Consent> consents,
    Captcha captcha) {

  public FormConfigResponse {
    optionGroups = Collections.unmodifiableMap(new LinkedHashMap<>(optionGroups));
    consents = List.copyOf(consents);
  }

  /** Active selectable option. */
  public record Option(String id, String name) {}

  /** Consent definition; text comes from configuration. */
  public record Consent(String id, String text, boolean required) {}

  /** Captcha mode; {@code testToken} only in non-production test mode. */
  public record Captcha(String mode, String siteKey, String testToken) {}
}
