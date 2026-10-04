package si.konferenca.registration.application;

import java.util.List;
import org.springframework.stereotype.Service;

/** Builds what the forms need: active options, consent wording, anti-automation mode. */
@Service
public class FormConfigService {

  private final OptionsCatalog catalog;
  private final PublicSettings settings;

  public FormConfigService(OptionsCatalog catalog, PublicSettings settings) {
    this.catalog = catalog;
    this.settings = settings;
  }

  /** The form configuration of the getFormConfig operation. */
  public record FormConfig(
      String conferenceName,
      ConferenceOptions.Consent consent,
      List<ConferenceOptions.Option> activeOptions,
      boolean captchaTestMode,
      String captchaSiteKey) {

    public FormConfig {
      activeOptions = List.copyOf(activeOptions);
    }
  }

  public FormConfig formConfig() {
    ConferenceOptions options = catalog.options();
    return new FormConfig(
        settings.conferenceName(),
        options.consent(),
        options.active(),
        settings.captchaTestMode(),
        settings.captchaTestMode() ? null : settings.captchaSiteKey());
  }
}
