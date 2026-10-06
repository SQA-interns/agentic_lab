package si.konferenca.registration.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.FormSettings;
import si.konferenca.registration.domain.ConferenceCatalog;
import si.konferenca.registration.domain.RegistrationType;

/** GET /api/form-config: active options, consents and anti-automation mode (US-003, AR-07). */
@RestController
public class FormConfigController {

  private final ConferenceCatalog catalog;
  private final FormSettings settings;

  public FormConfigController(ConferenceCatalog catalog, FormSettings settings) {
    this.catalog = catalog;
    this.settings = settings;
  }

  record Option(String id, String name, String category, List<String> offeredTo) {}

  record Consent(String id, String text, boolean mandatory) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Captcha(String mode, String siteKey) {}

  record FormConfig(
      String conferenceName, List<Option> options, List<Consent> consents, Captcha captcha) {}

  @GetMapping("/api/form-config")
  public FormConfig formConfig() {
    List<Option> options =
        catalog.activeOptions().stream()
            .map(
                o ->
                    new Option(
                        o.id(),
                        o.name(),
                        o.category().value(),
                        java.util.Arrays.stream(RegistrationType.values())
                            .filter(o::isOfferedTo)
                            .map(RegistrationType::value)
                            .toList()))
            .toList();
    List<Consent> consents =
        catalog.consents().stream().map(c -> new Consent(c.id(), c.text(), c.mandatory())).toList();
    Captcha captcha =
        settings.captchaTestMode()
            ? new Captcha("test", null)
            : new Captcha("live", settings.captchaSiteKey());
    return new FormConfig(settings.conferenceName(), options, consents, captcha);
  }
}
