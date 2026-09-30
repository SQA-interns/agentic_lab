package lab.conference.registration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lab.conference.options.Catalog;
import lab.conference.options.GroupId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/catalog: active options only, consent fixture and captcha mode (AC-003-01). */
@RestController
public class CatalogController {

  private final Map<String, Object> body;

  public CatalogController(Catalog catalog, CaptchaVerifier captcha) {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("conferenceTitle", catalog.conferenceTitle());
    Map<String, Object> captchaInfo = new LinkedHashMap<>();
    captchaInfo.put("mode", captcha.mode());
    captchaInfo.put("siteKey", captcha.siteKey());
    b.put("captcha", captchaInfo);
    b.put(
        "consent",
        catalog
            .consent()
            .map(
                c -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("id", c.id());
                  m.put("text", c.text());
                  m.put("required", c.required());
                  return m;
                })
            .orElse(null));
    List<Map<String, Object>> groups = new ArrayList<>();
    for (GroupId g : GroupId.values()) {
      Map<String, Object> group = new LinkedHashMap<>();
      group.put("id", g.key());
      group.put("label", g.label());
      group.put(
          "options",
          catalog.activeOptions(g).stream()
              .map(o -> Map.of("id", o.id(), "name", o.name()))
              .toList());
      groups.add(group);
    }
    b.put("groups", groups);
    this.body = b;
  }

  @GetMapping(path = "/api/catalog", produces = "application/json")
  public Map<String, Object> catalog() {
    return body;
  }
}
