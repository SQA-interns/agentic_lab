package si.konferenca.registration.acceptance.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Valid request bodies per openapi.yaml RegistrationRequest; tests change single fields. */
public final class Registrations {

  /** Token accepted by the reCAPTCHA test mode (recaptcha.schema.json). */
  public static final String CAPTCHA_PASS = "test-pass";

  private Registrations() {}

  public static String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.si";
  }

  public static Map<String, Object> external() {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("type", "EXTERNAL");
    r.put("firstName", "Ana");
    r.put("lastName", "Novak");
    r.put("email", uniqueEmail("ana"));
    r.put("organization", "Institut Jožef Stefan");
    r.put("optionIds", List.of("ws-ai", "meal-lunch"));
    r.put("consentGiven", true);
    r.put("recaptchaToken", CAPTCHA_PASS);
    return r;
  }

  public static Map<String, Object> student() {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("type", "STUDENT");
    r.put("firstName", "Luka");
    r.put("lastName", "Kranjc");
    r.put("email", uniqueEmail("luka"));
    r.put("studyInstitution", "Univerza v Mariboru");
    r.put("studyProgramme", "Informatika");
    r.put("studentId", "93120001");
    r.put("optionIds", List.of("ws-security", "ev-tour"));
    r.put("consentGiven", true);
    r.put("recaptchaToken", CAPTCHA_PASS);
    return r;
  }

  public static Map<String, Object> with(Map<String, Object> base, String field, Object value) {
    Map<String, Object> r = new LinkedHashMap<>(base);
    if (value == null) {
      r.remove(field);
    } else {
      r.put(field, value);
    }
    return r;
  }
}
