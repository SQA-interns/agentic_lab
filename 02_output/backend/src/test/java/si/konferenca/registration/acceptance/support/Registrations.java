package si.konferenca.registration.acceptance.support;

import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Valid registration requests (openapi.yaml, RegistrationRequest) over the options of
 * src/test/resources/acceptance/conference-options.json; tests change one thing at a time.
 */
public final class Registrations {

  public static final String CONSENT = "data-processing";
  public static final String CONSENT_TEXT =
      "I agree to the processing of my personal data for the registration and organisation of"
          + " the conference.";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private Registrations() {}

  /** A unique address, so tests never collide on the one-registration-per-email rule. */
  public static String uniqueEmail(String prefix) {
    return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@example.si";
  }

  public static ObjectNode external() {
    ObjectNode body = JSON.createObjectNode();
    body.put("type", "EXTERNAL");
    body.put("firstName", "Ana");
    body.put("lastName", "Novak");
    body.put("email", uniqueEmail("ana.novak"));
    body.put("organization", "Institut Jožef Stefan");
    options(body, "ws-ai", "ev-reception");
    consents(body, CONSENT);
    body.put("captchaToken", TestStack.CAPTCHA_TOKEN);
    return body;
  }

  public static ObjectNode student() {
    ObjectNode body = JSON.createObjectNode();
    body.put("type", "STUDENT");
    body.put("firstName", "Luka");
    body.put("lastName", "Kranjc");
    body.put("email", uniqueEmail("luka.kranjc"));
    body.put("studyInstitution", "Fakulteta za računalništvo in informatiko");
    body.put("studyProgramme", "Računalništvo in informatika");
    body.put("studentId", "63200001");
    options(body, "ws-security", "other-career-fair");
    consents(body, CONSENT);
    body.put("captchaToken", TestStack.CAPTCHA_TOKEN);
    return body;
  }

  public static ObjectNode options(ObjectNode body, String... optionIds) {
    ArrayNode array = body.putArray("optionIds");
    for (String id : optionIds) {
      array.add(id);
    }
    return body;
  }

  public static ObjectNode consents(ObjectNode body, String... consentIds) {
    ArrayNode array = body.putArray("consentIds");
    for (String id : consentIds) {
      array.add(id);
    }
    return body;
  }

  /** A string of {@code length} letters. */
  public static String letters(int length) {
    return "a".repeat(length);
  }

  /** A syntactically valid email address of exactly {@code length} characters. */
  public static String emailOfLength(int length) {
    String domain = "@example.si";
    return "x".repeat(length - domain.length()) + domain;
  }
}
