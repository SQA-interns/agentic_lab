package si.konferenca.registration.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.OptionCatalog;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;

/** GET /api/options: active options and consents (US-003, AC-003-01). */
@RestController
public class OptionsController {

  private final OptionCatalog catalog;

  public OptionsController(OptionCatalog catalog) {
    this.catalog = catalog;
  }

  /** One offered option. */
  public record OptionView(String id, String name, String category) {
    static OptionView of(ConferenceOption o) {
      return new OptionView(o.id(), o.name(), o.category().code());
    }
  }

  /** One consent as shown to participants. */
  public record ConsentView(String id, String text, boolean required) {
    static ConsentView of(ConsentDefinition c) {
      return new ConsentView(c.id(), c.text(), c.required());
    }
  }

  /** The OptionsResponse schema. */
  public record OptionsResponse(List<OptionView> options, List<ConsentView> consents) {}

  @GetMapping("/api/options")
  public OptionsResponse options() {
    return new OptionsResponse(
        catalog.activeOptions().stream().map(OptionView::of).toList(),
        catalog.consents().stream().map(ConsentView::of).toList());
  }
}
