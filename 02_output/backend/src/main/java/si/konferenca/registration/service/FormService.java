package si.konferenca.registration.service;

import java.util.List;
import org.springframework.stereotype.Service;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Consent;
import si.konferenca.registration.domain.OptionCatalogue;

/** Provides what the form needs: active options, limits, consents, reCAPTCHA settings. */
@Service
public class FormService {

  private final OptionCatalogueProvider catalogueProvider;
  private final AppProperties properties;

  public FormService(OptionCatalogueProvider catalogueProvider, AppProperties properties) {
    this.catalogueProvider = catalogueProvider;
    this.properties = properties;
  }

  public Form form() {
    OptionCatalogue catalogue = catalogueProvider.catalogue();
    List<CategoryLimit> limits =
        List.of(Category.values()).stream()
            .map(c -> new CategoryLimit(c, catalogue.limit(c)))
            .toList();
    AppProperties.Recaptcha recaptcha = properties.recaptcha();
    return new Form(
        properties.conferenceName(),
        recaptcha.testMode() ? "" : recaptcha.siteKey(),
        recaptcha.testMode(),
        limits,
        catalogue.activeOptions(),
        catalogue.consents());
  }

  /** Form configuration. */
  public record Form(
      String conferenceName,
      String recaptchaSiteKey,
      boolean recaptchaTestMode,
      List<CategoryLimit> categories,
      List<ConferenceOption> options,
      List<Consent> consents) {

    public Form {
      categories = List.copyOf(categories);
      options = List.copyOf(options);
      consents = List.copyOf(consents);
    }
  }

  /** Maximum selections of one category. */
  public record CategoryLimit(Category category, int maxSelections) {}
}
