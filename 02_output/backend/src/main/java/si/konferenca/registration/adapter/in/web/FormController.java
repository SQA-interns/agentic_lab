package si.konferenca.registration.adapter.in.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.FormQueries;
import si.konferenca.registration.domain.ConferenceOption;

/** The public data the registration form needs (openapi.yaml, tag "form"). */
@RestController
public class FormController {

  private final FormQueries formQueries;
  private final FormConfigResponse formConfig;

  public FormController(FormQueries formQueries, FormConfigResponse formConfig) {
    this.formQueries = formQueries;
    this.formConfig = formConfig;
  }

  /** Schema FormConfig. */
  public record FormConfigResponse(
      String conferenceName, ConsentResponse consent, CaptchaResponse captcha) {}

  /** The consent wording shown on the form. */
  public record ConsentResponse(String id, String text) {}

  /** The anti-automation mode and the reCAPTCHA site key (AR-07). */
  public record CaptchaResponse(String mode, String siteKey) {}

  /** Schema Option. */
  public record OptionResponse(String id, String name, String category) {}

  /** Schema OptionList. */
  public record OptionListResponse(List<OptionResponse> options) {
    public OptionListResponse {
      options = List.copyOf(options);
    }
  }

  @GetMapping("/api/form-config")
  public FormConfigResponse formConfig() {
    return formConfig;
  }

  @GetMapping("/api/options")
  public OptionListResponse options() {
    return new OptionListResponse(
        formQueries.activeOptions().stream().map(FormController::toResponse).toList());
  }

  private static OptionResponse toResponse(ConferenceOption option) {
    return new OptionResponse(option.id(), option.name(), option.category().code());
  }
}
