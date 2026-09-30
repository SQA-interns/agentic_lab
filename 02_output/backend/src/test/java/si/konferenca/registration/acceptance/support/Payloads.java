package si.konferenca.registration.acceptance.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registration request bodies as in 02_contracts/openapi.json (RegistrationRequest). Option and
 * consent ids come from src/test/resources/acceptance/conference-options.json.
 */
public final class Payloads {

  public static final String WORKSHOP = "ws-ai";
  public static final String WORKSHOP_NAME = "Delavnica umetne inteligence";
  public static final String INACTIVE_WORKSHOP = "ws-retired";
  public static final String EVENT = "ev-dinner";
  public static final String EVENT_NAME = "Conference dinner";
  public static final String MEAL = "meal-lunch";
  public static final String MEAL_NAME = "Kosilo, dan 1";
  public static final String OTHER = "other-tour";
  public static final String OTHER_NAME = "Ogled Ljubljane";
  public static final String PRIVACY = "privacy";
  public static final String NEWSLETTER = "newsletter";

  private Payloads() {}

  public static String uniqueEmail() {
    return "p-" + UUID.randomUUID().toString().substring(0, 12) + "@participants.test";
  }

  public static Map<String, Object> external() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", "EXTERNAL");
    m.put("firstName", "Ana");
    m.put("lastName", "Novak");
    m.put("email", uniqueEmail());
    m.put("organization", "Institut Jožef Stefan");
    m.put("optionIds", new ArrayList<>(List.of(WORKSHOP, MEAL)));
    m.put("consents", new ArrayList<>(List.of(PRIVACY)));
    m.put("recaptchaToken", TestEnvironment.TEST_MODE_TOKEN);
    return m;
  }

  public static Map<String, Object> student() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", "STUDENT");
    m.put("firstName", "Luka");
    m.put("lastName", "Kovač");
    m.put("email", uniqueEmail());
    m.put("studyInstitution", "Univerza v Ljubljani");
    m.put("studyProgramme", "Računalništvo in informatika");
    m.put("studentId", "63210001");
    m.put("optionIds", new ArrayList<>(List.of(WORKSHOP, EVENT, MEAL, OTHER)));
    m.put("consents", new ArrayList<>(List.of(PRIVACY, NEWSLETTER)));
    m.put("recaptchaToken", TestEnvironment.TEST_MODE_TOKEN);
    return m;
  }

  public static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
    Map<String, Object> m = new LinkedHashMap<>(base);
    m.put(key, value);
    return m;
  }

  public static Map<String, Object> without(Map<String, Object> base, String key) {
    Map<String, Object> m = new LinkedHashMap<>(base);
    m.remove(key);
    return m;
  }
}
