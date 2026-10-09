package si.konferenca.registration.application;

import java.util.Arrays;
import java.util.List;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.OptionCatalogue;

/** Everything the registration form needs (GET /api/registration-form). */
public class RegistrationFormService {

  /** Anti-automation mode shown to the form (SR-01, SR-02). */
  public enum CaptchaMode {
    RECAPTCHA,
    TEST
  }

  /** The form data. */
  public record RegistrationForm(
      String conferenceName,
      CaptchaMode captchaMode,
      String siteKey,
      List<CategoryOptions> categories,
      List<ConsentDefinition> consents) {
    public RegistrationForm {
      categories = List.copyOf(categories);
      consents = List.copyOf(consents);
    }
  }

  /** Active options of one category. */
  public record CategoryOptions(
      Category category, int maxSelections, List<ConferenceOption> options) {
    public CategoryOptions {
      options = List.copyOf(options);
    }
  }

  private final OptionCatalogue catalogue;
  private final String conferenceName;
  private final CaptchaMode captchaMode;
  private final String siteKey;

  public RegistrationFormService(
      OptionCatalogue catalogue, String conferenceName, CaptchaMode captchaMode, String siteKey) {
    this.catalogue = catalogue;
    this.conferenceName = conferenceName;
    this.captchaMode = captchaMode;
    this.siteKey = captchaMode == CaptchaMode.TEST ? null : siteKey;
  }

  public RegistrationForm form() {
    List<CategoryOptions> categories =
        Arrays.stream(Category.values())
            .map(
                category ->
                    new CategoryOptions(
                        category,
                        catalogue.maxSelections(category),
                        catalogue.activeOptions(category)))
            .toList();
    return new RegistrationForm(
        conferenceName, captchaMode, siteKey, categories, catalogue.consents());
  }
}
