package si.konferenca.registration.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.ConferenceCatalog;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.web.dto.ConferenceResponse;

@RestController
public class ConferenceController {

  private final ConferenceCatalog catalog;
  private final AppProperties.Recaptcha recaptcha;

  public ConferenceController(ConferenceCatalog catalog, AppProperties properties) {
    this.catalog = catalog;
    this.recaptcha = properties.recaptcha();
  }

  @GetMapping("/api/conference")
  public ConferenceResponse conference() {
    return new ConferenceResponse(
        catalog.activeOptions().stream().map(ConferenceResponse.OptionDto::of).toList(),
        catalog.consents().stream().map(ConferenceResponse.ConsentDto::of).toList(),
        new ConferenceResponse.RecaptchaDto(
            recaptcha.siteKey() == null ? "" : recaptcha.siteKey(), recaptcha.testMode()));
  }
}
