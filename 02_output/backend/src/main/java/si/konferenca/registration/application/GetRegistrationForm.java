package si.konferenca.registration.application;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceCatalogue;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Consent;
import si.konferenca.registration.domain.Field;
import si.konferenca.registration.domain.RegistrationType;

/** Use case: the form definition of one registration type (BR-01, BR-04, BR-05, US-003). */
@Service
public class GetRegistrationForm {

  /** What the frontend needs to render the anti-automation check (AR-07). */
  public record CaptchaSettings(boolean testMode, String siteKey) {}

  /** Options of one category with its limit. */
  public record CategoryView(Category category, int maxSelections, List<ConferenceOption> options) {
    public CategoryView {
      options = List.copyOf(options);
    }
  }

  /** The whole form. */
  public record FormView(
      RegistrationType type,
      List<Field> fields,
      List<CategoryView> categories,
      List<Consent> consents,
      CaptchaSettings captcha) {
    public FormView {
      fields = List.copyOf(fields);
      categories = List.copyOf(categories);
      consents = List.copyOf(consents);
    }
  }

  private final ConferenceCatalogue catalogue;
  private final CaptchaSettings captcha;

  public GetRegistrationForm(ConferenceCatalogue catalogue, CaptchaSettings captcha) {
    this.catalogue = catalogue;
    this.captcha = captcha;
  }

  public FormView form(RegistrationType type) {
    List<CategoryView> categories =
        Arrays.stream(Category.values())
            .map(
                c ->
                    new CategoryView(
                        c, catalogue.maxSelections(c), catalogue.selectableOptions(type, c)))
            .toList();
    return new FormView(type, Field.of(type), categories, catalogue.consents(), captcha);
  }
}
