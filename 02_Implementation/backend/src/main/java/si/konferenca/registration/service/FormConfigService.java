package si.konferenca.registration.service;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import si.konferenca.registration.domain.ConferenceOption;

/** Provides what the registration forms need: active options, consents and captcha settings. */
@Service
public class FormConfigService {

  private final ConferenceOptionCatalog optionCatalog;
  private final CaptchaVerifier captchaVerifier;

  public FormConfigService(ConferenceOptionCatalog optionCatalog, CaptchaVerifier captchaVerifier) {
    this.optionCatalog = optionCatalog;
    this.captchaVerifier = captchaVerifier;
  }

  /** Form configuration with only active options, ordered by category then configuration order. */
  public FormConfig formConfig() {
    List<ConferenceOption> activeOptions =
        optionCatalog.findAll().stream()
            .filter(ConferenceOption::active)
            .sorted(Comparator.comparing(ConferenceOption::category))
            .toList();
    return new FormConfig(activeOptions, Consents.ALL, captchaVerifier.settings());
  }

  /** The data needed to render the forms. */
  public record FormConfig(
      List<ConferenceOption> options,
      List<Consents.ConsentDefinition> consents,
      CaptchaVerifier.CaptchaSettings captcha) {}
}
