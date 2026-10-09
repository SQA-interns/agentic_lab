package si.konferenca.registration.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.GetRegistrationForm;
import si.konferenca.registration.application.GetRegistrationForm.FormView;
import si.konferenca.registration.domain.RegistrationType;

/** {@code GET /api/registration-form/{type}} (registration-api.openapi.yaml). */
@RestController
public class RegistrationFormController {

  private final GetRegistrationForm useCase;

  public RegistrationFormController(GetRegistrationForm useCase) {
    this.useCase = useCase;
  }

  @GetMapping("/api/registration-form/{type}")
  public ResponseEntity<?> form(@PathVariable String type) {
    return RegistrationType.fromPath(type)
        .<ResponseEntity<?>>map(t -> ResponseEntity.ok(view(useCase.form(t))))
        .orElseGet(() -> Problems.of(HttpStatus.NOT_FOUND));
  }

  private static Map<String, Object> view(FormView form) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", form.type().name());
    body.put(
        "fields",
        form.fields().stream()
            .map(f -> Map.<String, Object>of("name", f.apiName(), "maxLength", f.maxLength()))
            .toList());
    body.put(
        "categories",
        form.categories().stream()
            .map(
                c -> {
                  Map<String, Object> category = new LinkedHashMap<>();
                  category.put("category", c.category().name());
                  category.put("maxSelections", c.maxSelections());
                  category.put(
                      "options",
                      c.options().stream()
                          .map(o -> Map.<String, Object>of("id", o.id(), "name", o.name()))
                          .toList());
                  return category;
                })
            .toList());
    body.put(
        "consents",
        form.consents().stream()
            .map(
                c -> {
                  Map<String, Object> consent = new LinkedHashMap<>();
                  consent.put("id", c.id());
                  consent.put("text", c.text());
                  consent.put("mandatory", c.mandatory());
                  return consent;
                })
            .toList());
    Map<String, Object> recaptcha = new LinkedHashMap<>();
    if (form.captcha().testMode()) {
      recaptcha.put("mode", "TEST");
    } else {
      recaptcha.put("mode", "GOOGLE");
      recaptcha.put("siteKey", form.captcha().siteKey());
    }
    body.put("recaptcha", recaptcha);
    return body;
  }
}
