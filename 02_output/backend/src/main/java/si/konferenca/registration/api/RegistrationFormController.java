package si.konferenca.registration.api;

import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.RegistrationFormService;
import si.konferenca.registration.domain.RegistrationType;

/** GET /api/registration-form (openapi.yaml, getRegistrationForm). */
@RestController
public class RegistrationFormController {

  private final RegistrationFormService formService;

  public RegistrationFormController(RegistrationFormService formService) {
    this.formService = formService;
  }

  /** Response body (openapi.yaml, RegistrationForm). */
  public record RegistrationFormResponse(
      String conferenceName,
      Captcha captcha,
      List<CategoryOptions> categories,
      List<Consent> consents) {
    public RegistrationFormResponse {
      categories = List.copyOf(categories);
      consents = List.copyOf(consents);
    }
  }

  /** Anti-automation mode and site key. */
  public record Captcha(String mode, String siteKey) {}

  /** Active options of one category. */
  public record CategoryOptions(String category, int maxSelections, List<FormOption> options) {
    public CategoryOptions {
      options = List.copyOf(options);
    }
  }

  /** One option. */
  public record FormOption(String id, String name, List<RegistrationType> availableTo) {
    public FormOption {
      availableTo = List.copyOf(availableTo);
    }
  }

  /** One consent. */
  public record Consent(String id, String text, boolean mandatory) {}

  @GetMapping("/api/registration-form")
  public RegistrationFormResponse registrationForm() {
    RegistrationFormService.RegistrationForm form = formService.form();
    return new RegistrationFormResponse(
        form.conferenceName(),
        new Captcha(form.captchaMode().name().toLowerCase(Locale.ROOT), form.siteKey()),
        form.categories().stream()
            .map(
                category ->
                    new CategoryOptions(
                        category.category().value(),
                        category.maxSelections(),
                        category.options().stream()
                            .map(
                                option ->
                                    new FormOption(
                                        option.id(),
                                        option.name(),
                                        option.availableTo().stream().sorted().toList()))
                            .toList()))
            .toList(),
        form.consents().stream()
            .map(consent -> new Consent(consent.id(), consent.text(), consent.mandatory()))
            .toList());
  }
}
