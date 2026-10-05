package si.konferenca.registration.acceptance.support;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Valid registration requests (api.openapi.yaml) using the options and consents of
 * src/test/resources/acceptance/conference-config.json. Tests change single properties.
 */
public final class Registrations {

  public static final String CONSENT_DATA = "data-processing";
  public static final String CONSENT_PHOTO = "photo";
  public static final String OPTION_WORKSHOP = "ws-testing";
  public static final String OPTION_WORKSHOP_NAME = "Delavnica: testiranje";
  public static final String OPTION_EXTERNAL_ONLY = "ws-industry";
  public static final String OPTION_INACTIVE = "ws-cancelled";
  public static final String OPTION_EVENT = "ev-dinner";
  public static final String OPTION_MEAL = "meal-lunch";
  public static final String OPTION_MEAL_NAME = "Kosilo";
  public static final String OPTION_OTHER = "other-tour";

  private Registrations() {}

  public static ObjectNode external() {
    ObjectNode n = Api.JSON.createObjectNode();
    n.put("type", "external");
    n.put("firstName", "Ana");
    n.put("lastName", "Novak");
    n.put("email", "ana.novak@example.com");
    n.put("organization", "Institut Jozef Stefan");
    ArrayNode options = n.putArray("optionIds");
    options.add(OPTION_WORKSHOP);
    options.add(OPTION_MEAL);
    n.putArray("consents").add(CONSENT_DATA);
    n.put("captchaToken", TestEnvironment.CAPTCHA_PASS);
    return n;
  }

  public static ObjectNode student() {
    ObjectNode n = Api.JSON.createObjectNode();
    n.put("type", "student");
    n.put("firstName", "Luka");
    n.put("lastName", "Kranjc");
    n.put("email", "luka.kranjc@student.example.com");
    n.put("studyInstitution", "Univerza v Mariboru");
    n.put("studyProgramme", "Informatika");
    n.put("studentId", "93120045");
    ArrayNode options = n.putArray("optionIds");
    options.add(OPTION_WORKSHOP);
    options.add(OPTION_OTHER);
    n.putArray("consents").add(CONSENT_DATA);
    n.put("captchaToken", TestEnvironment.CAPTCHA_PASS);
    return n;
  }

  public static ObjectNode withOptions(ObjectNode request, String... optionIds) {
    ArrayNode options = request.putArray("optionIds");
    for (String id : optionIds) {
      options.add(id);
    }
    return request;
  }

  public static ObjectNode withConsents(ObjectNode request, String... consentIds) {
    ArrayNode consents = request.putArray("consents");
    for (String id : consentIds) {
      consents.add(id);
    }
    return request;
  }
}
