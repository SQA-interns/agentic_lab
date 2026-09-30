package lab.conference.acceptance.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Valid request bodies for the two forms (docs/02_contracts/openapi.yaml), built from catalog-v1.
 */
public final class Payloads {

  private Payloads() {}

  public static String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 12) + "@example.test";
  }

  public static Map<String, Object> external() {
    Map<String, Object> body = common();
    body.put("organization", "Synthetic Institute d.o.o.");
    return body;
  }

  public static Map<String, Object> student() {
    Map<String, Object> body = common();
    body.put("studyInstitution", "Synthetic University");
    body.put("studyProgramme", "Computer Science");
    body.put("studentId", "S-12345/Č");
    return body;
  }

  private static Map<String, Object> common() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("clientRequestId", UUID.randomUUID().toString());
    body.put("captchaToken", AppInstance.CAPTCHA_OK);
    body.put("firstName", "Ana");
    body.put("lastName", "Kovač");
    body.put("email", uniqueEmail("participant"));
    body.put(
        "selections", selections(List.of("ws-alpha"), List.of("ev-gala"), List.of(), List.of()));
    body.put("consentGiven", true);
    return body;
  }

  public static Map<String, Object> selections(
      List<String> workshops, List<String> events, List<String> meals, List<String> other) {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("workshops", new ArrayList<>(workshops));
    s.put("events", new ArrayList<>(events));
    s.put("meals", new ArrayList<>(meals));
    s.put("other", new ArrayList<>(other));
    return s;
  }

  /** A copy with a new client request ID and email. */
  public static Map<String, Object> fresh(Map<String, Object> body) {
    Map<String, Object> copy = new LinkedHashMap<>(body);
    copy.put("clientRequestId", UUID.randomUUID().toString());
    copy.put("email", uniqueEmail("participant"));
    return copy;
  }
}
