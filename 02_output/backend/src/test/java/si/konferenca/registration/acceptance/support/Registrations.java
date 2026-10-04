package si.konferenca.registration.acceptance.support;

import java.util.List;
import java.util.UUID;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Valid registration requests with unique email addresses, to be varied by each test. */
public final class Registrations {

  public static final String CAPTCHA_PASS = "test-pass";
  public static final List<String> DEFAULT_OPTIONS = List.of("ws-ai", "meal-lunch-day1");

  private Registrations() {}

  public static String uniqueEmail(String prefix) {
    return prefix + "." + UUID.randomUUID().toString().substring(0, 12) + "@example.si";
  }

  public static ObjectNode external() {
    ObjectNode r = common("EXTERNAL", "ana.novak");
    r.put("firstName", "Ana");
    r.put("lastName", "Novak");
    r.put("organization", "Univerza v Mariboru");
    return r;
  }

  public static ObjectNode student() {
    ObjectNode r = common("STUDENT", "luka.kranjc");
    r.put("firstName", "Luka");
    r.put("lastName", "Kranjc");
    r.put("studyInstitution", "Univerza v Ljubljani");
    r.put("studyProgramme", "Računalništvo in informatika");
    r.put("studentId", "63210042");
    return r;
  }

  public static ObjectNode withOptions(ObjectNode registration, String... optionIds) {
    ArrayNode options = registration.putArray("optionIds");
    for (String id : optionIds) {
      options.add(id);
    }
    return registration;
  }

  public static String email(ObjectNode registration) {
    return registration.get("email").asString();
  }

  private static ObjectNode common(String type, String emailPrefix) {
    ObjectNode r = ApiClient.JSON.createObjectNode();
    r.put("type", type);
    r.put("email", uniqueEmail(emailPrefix));
    withOptions(r, DEFAULT_OPTIONS.toArray(String[]::new));
    r.put("consentGiven", true);
    r.put("captchaToken", CAPTCHA_PASS);
    return r;
  }
}
