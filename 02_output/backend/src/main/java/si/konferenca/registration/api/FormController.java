package si.konferenca.registration.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Consent;
import si.konferenca.registration.service.FormService;

/** {@code GET /api/form} (api.openapi.yaml FormConfig). */
@RestController
@RequestMapping("/api")
public class FormController {

  private final FormService formService;

  public FormController(FormService formService) {
    this.formService = formService;
  }

  @GetMapping("/form")
  public FormConfig form() {
    FormService.Form form = formService.form();
    return new FormConfig(
        form.conferenceName(),
        new RecaptchaConfig(form.recaptchaSiteKey(), form.recaptchaTestMode()),
        form.categories().stream()
            .map(c -> new CategoryConfig(c.category().name(), c.maxSelections()))
            .toList(),
        form.options().stream().map(FormController::option).toList(),
        form.consents().stream().map(FormController::consent).toList());
  }

  private static OptionConfig option(ConferenceOption o) {
    return new OptionConfig(
        o.id(),
        o.name(),
        o.category().name(),
        o.registrationTypes().stream().map(Enum::name).sorted().toList());
  }

  private static ConsentConfig consent(Consent c) {
    return new ConsentConfig(c.id(), c.text(), c.mandatory());
  }

  /** Response body. */
  public record FormConfig(
      String conferenceName,
      RecaptchaConfig recaptcha,
      List<CategoryConfig> categories,
      List<OptionConfig> options,
      List<ConsentConfig> consents) {

    public FormConfig {
      categories = List.copyOf(categories);
      options = List.copyOf(options);
      consents = List.copyOf(consents);
    }
  }

  /** reCAPTCHA settings for the widget. */
  public record RecaptchaConfig(String siteKey, boolean testMode) {}

  /** Category limit. */
  public record CategoryConfig(String category, int maxSelections) {}

  /** Active option. */
  public record OptionConfig(
      String id, String name, String category, List<String> registrationTypes) {

    public OptionConfig {
      registrationTypes = List.copyOf(registrationTypes);
    }
  }

  /** Consent. */
  public record ConsentConfig(String id, String text, boolean mandatory) {}
}
