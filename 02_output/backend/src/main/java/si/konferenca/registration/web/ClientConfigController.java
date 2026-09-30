package si.konferenca.registration.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.settings.AppProperties;

/** GET /api/config: the public client configuration (AR-07). Never exposes a secret. */
@RestController
public class ClientConfigController {

  private final AppProperties properties;

  public ClientConfigController(AppProperties properties) {
    this.properties = properties;
  }

  /** The ClientConfig schema. */
  public record ClientConfig(
      String recaptchaSiteKey, boolean recaptchaTestMode, String conferenceName) {}

  @GetMapping("/api/config")
  public ClientConfig config() {
    boolean testMode = properties.recaptcha().testMode();
    String siteKey =
        testMode || properties.recaptcha().siteKey() == null
            ? ""
            : properties.recaptcha().siteKey();
    return new ClientConfig(siteKey, testMode, properties.conferenceName());
  }
}
