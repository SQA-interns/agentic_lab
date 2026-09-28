package si.konferenca.registration.service;

import java.io.IOException;
import java.io.OutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import si.konferenca.registration.domain.RegistrationRepository;

/** Organizer export of the current registrations as an Excel workbook. */
@Service
public class ExportService {

  private final RegistrationRepository repository;
  private final RegistrationExportWriter exportWriter;

  public ExportService(RegistrationRepository repository, RegistrationExportWriter exportWriter) {
    this.repository = repository;
    this.exportWriter = exportWriter;
  }

  @Transactional(readOnly = true)
  public void exportAll(OutputStream out) throws IOException {
    exportWriter.write(repository.findAllByOrderByCreatedAtAsc(), out);
  }
}
