package si.konferenca.registration.service;

import org.springframework.stereotype.Service;
import si.konferenca.registration.domain.OptionCatalogue;
import si.konferenca.registration.infrastructure.OptionsFileReader;

/** Holds the option catalogue read once at startup; a restart applies changes (AC-003-02). */
@Service
public class OptionCatalogueProvider {

  private final OptionCatalogue catalogue;

  public OptionCatalogueProvider(OptionsFileReader reader) {
    this.catalogue = reader.read();
  }

  public OptionCatalogue catalogue() {
    return catalogue;
  }
}
