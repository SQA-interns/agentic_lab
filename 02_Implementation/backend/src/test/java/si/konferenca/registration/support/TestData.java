package si.konferenca.registration.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Request payload builders and constants shared by tests. */
public final class TestData {

  public static final String ORGANIZER_USERNAME = "organizer";
  public static final String ORGANIZER_PASSWORD = "test-organizer-secret";
  public static final String ORGANIZER_EMAIL = "organizers@conference.test";
  public static final String CAPTCHA_TOKEN = "test-mode-pass";

  private TestData() {}

  /** A valid external participant registration body. */
  public static Map<String, Object> external() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("firstName", "Ana");
    body.put("lastName", "Novak");
    body.put("email", "ana.novak@example.si");
    body.put("organization", "Institut Jožef Stefan");
    body.put("optionIds", List.of("ws-ai-practice", "meal-lunch-day1"));
    body.put("consents", Map.of("privacy", true));
    body.put("captchaToken", CAPTCHA_TOKEN);
    return body;
  }

  /** A valid student registration body. */
  public static Map<String, Object> student() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("firstName", "Žiga");
    body.put("lastName", "Čeh");
    body.put("email", "ziga.ceh@student.uni-lj.si");
    body.put("studyInstitution", "Univerza v Ljubljani");
    body.put("studyProgramme", "Računalništvo in informatika");
    body.put("studentId", "63210001");
    body.put("optionIds", List.of("ev-conference-dinner"));
    body.put("consents", Map.of("privacy", true));
    body.put("captchaToken", CAPTCHA_TOKEN);
    return body;
  }
}
