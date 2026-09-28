package org.example.conference.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Synthetic request payloads (no real personal data). */
public final class Payloads {

  public static final String CAPTCHA = "test-captcha-pass";
  public static final String CONSENT_ID = "synthetic-required-consent";

  private Payloads() {}

  public static Map<String, Object> external() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("clientRequestId", UUID.randomUUID().toString());
    body.put("firstName", " Špela ");
    body.put("lastName", "Novak Čebašek ");
    body.put("email", "spela.synthetic@example.org");
    body.put("organization", "Inštitut za žabe");
    body.put("selections", selections(List.of("ws-data-science"), List.of("meal-lunch-day1")));
    body.put("consents", Map.of(CONSENT_ID, true));
    body.put("captchaToken", CAPTCHA);
    return body;
  }

  public static Map<String, Object> student() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("clientRequestId", UUID.randomUUID().toString());
    body.put("firstName", "Luka");
    body.put("lastName", "Študent");
    body.put("email", "luka.synthetic@student.example.org");
    body.put("studyInstitution", "Univerza v Ljubljani");
    body.put("studyProgramme", "Računalništvo");
    body.put("studentId", "S-000123");
    body.put(
        "selections", selections(List.of("ws-open-source"), List.of("meal-gala-dinner")));
    body.put("consents", Map.of(CONSENT_ID, true));
    body.put("captchaToken", CAPTCHA);
    return body;
  }

  public static Map<String, Object> selections(List<String> workshops, List<String> meals) {
    Map<String, Object> selections = new LinkedHashMap<>();
    selections.put("workshops", workshops);
    selections.put("events", List.of());
    selections.put("meals", meals);
    selections.put("otherActivities", List.of());
    return selections;
  }
}
