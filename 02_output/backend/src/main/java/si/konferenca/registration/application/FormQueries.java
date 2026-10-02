package si.konferenca.registration.application;

import java.util.List;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionsCatalogue;

/** Use case: what the registration form needs to display itself. */
public class FormQueries {

  private final OptionsCatalogue catalogue;

  public FormQueries(OptionsCatalogue catalogue) {
    this.catalogue = catalogue;
  }

  public List<ConferenceOption> activeOptions() {
    return catalogue.activeOptions();
  }
}
