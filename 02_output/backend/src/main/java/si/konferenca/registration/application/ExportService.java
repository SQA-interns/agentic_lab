package si.konferenca.registration.application;

import si.konferenca.registration.application.RegistrationPorts.ExportWriter;
import si.konferenca.registration.application.RegistrationPorts.RegistrationRepository;

/** Organizer export of all current registrations (US-008, BR-08); access is checked before. */
public class ExportService {

  private final RegistrationRepository repository;
  private final ExportWriter writer;

  public ExportService(RegistrationRepository repository, ExportWriter writer) {
    this.repository = repository;
    this.writer = writer;
  }

  public byte[] export() {
    return writer.write(repository.findAll());
  }
}
