package si.konferenca.registration.acceptance.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Test data built from the contracts in docs/02_contracts. */
public final class Fixtures {

  /** Options configuration used by the default test context (options-config.schema.json). */
  public static final String OPTIONS_CONFIG =
      """
      {
        "options": [
          { "id": "ws-secure-web", "name": "Workshop: Secure web development", "category": "WORKSHOP", "active": true },
          { "id": "ws-data-science", "name": "Workshop: Data science in practice", "category": "WORKSHOP", "active": true },
          { "id": "ws-legacy", "name": "Workshop: Legacy systems", "category": "WORKSHOP", "active": false },
          { "id": "ev-welcome", "name": "Welcome reception", "category": "EVENT", "active": true },
          { "id": "ev-gala-dinner", "name": "Gala dinner", "category": "EVENT", "active": true, "registrationTypes": ["EXTERNAL"] },
          { "id": "ev-career-fair", "name": "Student career fair", "category": "EVENT", "active": true, "registrationTypes": ["STUDENT"] },
          { "id": "meal-lunch-day1", "name": "Lunch, day 1", "category": "MEAL", "active": true },
          { "id": "meal-lunch-day2", "name": "Lunch, day 2", "category": "MEAL", "active": true },
          { "id": "meal-vegetarian", "name": "Vegetarian meals", "category": "MEAL", "active": true },
          { "id": "other-city-tour", "name": "Ljubljana city tour", "category": "OTHER", "active": true }
        ],
        "categoryLimits": { "MEAL": 2 },
        "consents": [
          { "id": "data-processing", "text": "I agree that the organizers process my personal data for conference registration.", "mandatory": true },
          { "id": "photos", "text": "I agree to appear in conference photos.", "mandatory": false }
        ]
      }
      """;

  public static final String MANDATORY_CONSENT = "data-processing";

  private Fixtures() {}

  /** A unique email address, so each test can find exactly its own data. */
  public static String uniqueEmail() {
    return "p-" + UUID.randomUUID().toString().substring(0, 12) + "@example.si";
  }

  /** A valid external participant registration (AC-001-01). */
  public static Map<String, Object> external(String email) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("type", "EXTERNAL");
    r.put("firstName", "Ana");
    r.put("lastName", "Novak");
    r.put("email", email);
    r.put("organization", "Institut Jožef Stefan");
    r.put("optionIds", new ArrayList<>(List.of("ws-secure-web", "meal-lunch-day1")));
    r.put("consentIds", new ArrayList<>(List.of(MANDATORY_CONSENT)));
    r.put("captchaToken", TestInfrastructure.TEST_MODE_TOKEN);
    return r;
  }

  /** A valid student registration (AC-002-01). */
  public static Map<String, Object> student(String email) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("type", "STUDENT");
    r.put("firstName", "Luka");
    r.put("lastName", "Kovač");
    r.put("email", email);
    r.put("studyInstitution", "Univerza v Ljubljani");
    r.put("studyProgramme", "Računalništvo in informatika");
    r.put("studentId", "63200001");
    r.put("optionIds", new ArrayList<>(List.of("ev-career-fair", "meal-lunch-day2")));
    r.put("consentIds", new ArrayList<>(List.of(MANDATORY_CONSENT)));
    r.put("captchaToken", TestInfrastructure.TEST_MODE_TOKEN);
    return r;
  }
}
