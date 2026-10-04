package si.konferenca.registration.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.FormConfigService;

/** GET /api/form-config (registration-api.openapi.yaml, getFormConfig). */
@RestController
public class FormConfigController {

  private final FormConfigService service;

  public FormConfigController(FormConfigService service) {
    this.service = service;
  }

  /** Response body of getFormConfig. */
  public record FormConfigResponse(
      String conferenceName, Consent consent, List<Option> options, Captcha captcha) {

    public FormConfigResponse {
      options = List.copyOf(options);
    }
  }

  /** Consent wording. */
  public record Consent(String id, String text) {}

  /** One active option. */
  public record Option(String id, String name, String category) {}

  /** Anti-automation mode and site key. */
  public record Captcha(boolean testMode, String siteKey) {}

  @GetMapping("/api/form-config")
  public FormConfigResponse formConfig() {
    FormConfigService.FormConfig config = service.formConfig();
    return new FormConfigResponse(
        config.conferenceName(),
        new Consent(config.consent().id(), config.consent().text()),
        config.activeOptions().stream()
            .map(o -> new Option(o.id(), o.name(), o.category().value()))
            .toList(),
        new Captcha(config.captchaTestMode(), config.captchaSiteKey()));
  }
}
