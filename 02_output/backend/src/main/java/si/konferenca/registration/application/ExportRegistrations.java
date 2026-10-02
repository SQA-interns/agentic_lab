package si.konferenca.registration.application;

import si.konferenca.registration.domain.RegistrationExporter;
import si.konferenca.registration.domain.RegistrationStore;

/** Use case: the organizer exports the current registrations (BR-08). */
public class ExportRegistrations {

  private final RegistrationStore store;
  private final RegistrationExporter exporter;

  public ExportRegistrations(RegistrationStore store, RegistrationExporter exporter) {
    this.store = store;
    this.exporter = exporter;
  }

  /** A workbook with every stored registration, oldest first. */
  public byte[] export() {
    return exporter.export(store.findAll());
  }
}
