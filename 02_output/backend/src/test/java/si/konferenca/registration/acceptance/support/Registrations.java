package si.konferenca.registration.acceptance.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Valid registration request bodies (api.openapi.yaml RegistrationRequest) with unique emails. */
public final class Registrations {

  private Registrations() {}

  public static String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 12) + "@example.si";
  }

  public static Map<String, Object> external() {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("type", "EXTERNAL");
    r.put("firstName", "Ana");
    r.put("lastName", "Novak");
    r.put("email", uniqueEmail("ext"));
    r.put("organization", "Institut Primer");
    r.put("optionIds", new ArrayList<>(List.of("ws-testing", "ev-reception", "meal-dinner")));
    r.put("consentIds", new ArrayList<>(List.of("data-processing")));
    r.put("recaptchaToken", RecaptchaMock.VALID_TOKEN);
    return r;
  }

  public static Map<String, Object> student() {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("type", "STUDENT");
    r.put("firstName", "Luka");
    r.put("lastName", "Horvat");
    r.put("email", uniqueEmail("stu"));
    r.put("studyInstitution", "Univerza v Mariboru");
    r.put("studyProgramme", "Informatika");
    r.put("studentId", "E1234567");
    r.put("optionIds", new ArrayList<>(List.of("ws-testing", "ev-career-fair", "meal-lunch-day1")));
    r.put("consentIds", new ArrayList<>(List.of("data-processing")));
    r.put("recaptchaToken", RecaptchaMock.VALID_TOKEN);
    return r;
  }

  public static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
    Map<String, Object> copy = new LinkedHashMap<>(base);
    copy.put(key, value);
    return copy;
  }

  public static Map<String, Object> without(Map<String, Object> base, String key) {
    Map<String, Object> copy = new LinkedHashMap<>(base);
    copy.remove(key);
    return copy;
  }
}
