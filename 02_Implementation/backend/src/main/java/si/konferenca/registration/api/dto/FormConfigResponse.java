package si.konferenca.registration.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.service.CaptchaVerifier;
import si.konferenca.registration.service.FormConfigService;

/** Data needed by the frontend to render the registration forms. */
public record FormConfigResponse(
    List<OptionItem> options, List<ConsentItem> consents, CaptchaItem captcha) {

  /** An active option. */
  public record OptionItem(String id, String name, OptionCategory category) {}

  /** A consent checkbox. */
  public record ConsentItem(String id, String text, boolean mandatory) {}

  /** Captcha widget configuration. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CaptchaItem(CaptchaVerifier.CaptchaSettings.Mode mode, String siteKey) {}

  public static FormConfigResponse from(FormConfigService.FormConfig config) {
    return new FormConfigResponse(
        config.options().stream().map(o -> new OptionItem(o.id(), o.name(), o.category())).toList(),
        config.consents().stream()
            .map(c -> new ConsentItem(c.id(), c.text(), c.mandatory()))
            .toList(),
        new CaptchaItem(config.captcha().mode(), config.captcha().siteKey()));
  }
}
