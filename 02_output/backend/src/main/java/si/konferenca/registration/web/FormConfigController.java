package si.konferenca.registration.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceCatalog;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.RegistrationType;

/** GET /api/form-config: active options, consents and anti-automation settings (US-003). */
@RestController
public class FormConfigController {

  private final ConferenceCatalog catalog;
  private final AppProperties app;

  public FormConfigController(ConferenceCatalog catalog, AppProperties app) {
    this.catalog = catalog;
    this.app = app;
  }

  @GetMapping("/api/form-config")
  public FormConfig formConfig() {
    List<CategoryBody> categories =
        Arrays.stream(Category.values())
            .map(c -> new CategoryBody(c.name(), catalog.limit(c).orElse(null)))
            .toList();
    List<OptionBody> options = catalog.activeOptions().stream().map(OptionBody::of).toList();
    List<ConsentBody> consents =
        catalog.consents().stream().map(c -> new ConsentBody(c.id(), c.text())).toList();
    AntiAutomation antiAutomation =
        app.recaptcha().testMode()
            ? new AntiAutomation("test", null)
            : new AntiAutomation("live", app.recaptcha().siteKey());
    return new FormConfig(app.conferenceName(), categories, options, consents, antiAutomation);
  }

  /** `FormConfig` of openapi.yaml. */
  public record FormConfig(
      String conferenceName,
      List<CategoryBody> categories,
      List<OptionBody> options,
      List<ConsentBody> consents,
      AntiAutomation antiAutomation) {

    public FormConfig {
      categories = List.copyOf(categories);
      options = List.copyOf(options);
      consents = List.copyOf(consents);
    }
  }

  /** A category with its optional maximum. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CategoryBody(String id, Integer maxSelections) {}

  /** An active option. */
  public record OptionBody(
      String id, String displayName, String category, List<String> availableTo) {

    public OptionBody {
      availableTo = List.copyOf(availableTo);
    }

    static OptionBody of(ConferenceOption option) {
      List<String> types =
          Arrays.stream(RegistrationType.values())
              .filter(option::availableTo)
              .map(Enum::name)
              .toList();
      return new OptionBody(option.id(), option.displayName(), option.category().name(), types);
    }
  }

  /** A mandatory consent. */
  public record ConsentBody(String id, String text) {}

  /** Anti-automation mode; the site key only in live mode (AR-07). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AntiAutomation(String mode, String siteKey) {}
}
