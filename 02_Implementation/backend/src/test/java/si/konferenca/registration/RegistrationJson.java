package si.konferenca.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds registration request bodies for API tests. */
public final class RegistrationJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Map<String, Object> body = new LinkedHashMap<>();

  private RegistrationJson() {}

  public static RegistrationJson external() {
    RegistrationJson json = new RegistrationJson();
    json.body.put("type", "EXTERNAL");
    json.body.put("firstName", "Žiga");
    json.body.put("lastName", "Šušteršič");
    json.body.put("email", "ziga@example.si");
    json.body.put("organization", "Univerza v Ljubljani — FRI");
    json.body.put("optionIds", List.of("ws-testing", "meal-lunch"));
    json.body.put("consentIds", List.of("privacy"));
    json.body.put("recaptchaToken", "test-mode-token");
    return json;
  }

  public static RegistrationJson student() {
    RegistrationJson json = new RegistrationJson();
    json.body.put("type", "STUDENT");
    json.body.put("firstName", "Ana");
    json.body.put("lastName", "Čeč");
    json.body.put("email", "ana@student.example.si");
    json.body.put("studyInstitution", "Fakulteta za računalništvo in informatiko");
    json.body.put("studyProgramme", "Računalništvo in informatika");
    json.body.put("studentId", "63210001");
    json.body.put("optionIds", List.of("ev-dinner"));
    json.body.put("consentIds", List.of("privacy"));
    json.body.put("recaptchaToken", "test-mode-token");
    return json;
  }

  public RegistrationJson with(String field, Object value) {
    body.put(field, value);
    return this;
  }

  public RegistrationJson without(String field) {
    body.remove(field);
    return this;
  }

  public String build() {
    try {
      return MAPPER.writeValueAsString(body);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
