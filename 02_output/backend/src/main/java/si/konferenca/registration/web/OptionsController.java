package si.konferenca.registration.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.RegistrationSetup;
import si.konferenca.registration.application.SetupQuery;

/** GET /api/options: the form setup (openapi.yaml RegistrationSetup, US-003, AR-07). */
@RestController
public class OptionsController {

  private final SetupQuery query;

  public OptionsController(SetupQuery query) {
    this.query = query;
  }

  @GetMapping("/api/options")
  ResponseEntity<Map<String, Object>> options() {
    RegistrationSetup s = query.setup();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("conferenceName", s.conferenceName());
    body.put("consent", Map.of("id", s.consent().id(), "text", s.consent().text()));
    body.put("recaptcha", Map.of("testMode", s.captchaTestMode(), "siteKey", s.captchaSiteKey()));
    body.put(
        "options",
        s.options().stream()
            .map(
                o -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("id", o.id());
                  m.put("name", o.name());
                  m.put("category", o.category().name());
                  m.put("offeredTo", o.offeredTo().stream().sorted().map(Enum::name).toList());
                  return m;
                })
            .toList());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
