package si.konferenca.registration.application;

import si.konferenca.registration.domain.RegistrationRepository;

/** All accepted registrations as a workbook, oldest first (US-008). */
public class ExportService {

  private final RegistrationRepository repository;
  private final RegistrationExporter exporter;

  public ExportService(RegistrationRepository repository, RegistrationExporter exporter) {
    this.repository = repository;
    this.exporter = exporter;
  }

  public byte[] exportAll() {
    return exporter.export(repository.findAllByOrderByAcceptedAtAscIdAsc());
  }
}
