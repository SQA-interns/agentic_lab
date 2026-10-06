package si.konferenca.registration.acceptance.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Valid `RegistrationRequest` bodies (`openapi.yaml`) that a test then varies. */
public final class Payloads {

  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  private Payloads() {}

  public static String uniqueEmail(String prefix) {
    return prefix + "." + System.nanoTime() + "." + SEQUENCE.incrementAndGet() + "@example.com";
  }

  public static Map<String, Object> external() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "EXTERNAL");
    body.put("firstName", "Janez");
    body.put("lastName", "Novak");
    body.put("email", uniqueEmail("janez.novak"));
    body.put("organization", "Institute of Testing");
    body.put("optionIds", new ArrayList<>(List.of("ws-ai-research", "meal-lunch-day1")));
    body.put("consentIds", new ArrayList<>(List.of(AcceptanceEnvironment.CONSENT_ID)));
    body.put("antiAutomationToken", AcceptanceEnvironment.TEST_MODE_TOKEN);
    return body;
  }

  public static Map<String, Object> student() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "STUDENT");
    body.put("firstName", "Ana");
    body.put("lastName", "Horvat");
    body.put("email", uniqueEmail("ana.horvat"));
    body.put("studyInstitution", "University of Ljubljana");
    body.put("studyProgramme", "Computer Science");
    body.put("studentId", "63210001");
    body.put("optionIds", new ArrayList<>(List.of("ws-open-data", "meal-lunch-day2")));
    body.put("consentIds", new ArrayList<>(List.of(AcceptanceEnvironment.CONSENT_ID)));
    body.put("antiAutomationToken", AcceptanceEnvironment.TEST_MODE_TOKEN);
    return body;
  }

  /** A copy of the body with one property replaced. */
  public static Map<String, Object> with(Map<String, Object> body, String property, Object value) {
    Map<String, Object> copy = new LinkedHashMap<>(body);
    copy.put(property, value);
    return copy;
  }

  /** A copy of the body without one property. */
  public static Map<String, Object> without(Map<String, Object> body, String property) {
    Map<String, Object> copy = new LinkedHashMap<>(body);
    copy.remove(property);
    return copy;
  }
}
