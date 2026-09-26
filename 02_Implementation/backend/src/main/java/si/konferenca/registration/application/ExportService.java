package si.konferenca.registration.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import si.konferenca.registration.application.port.RegistrationWorkbookWriter;
import si.konferenca.registration.persistence.RegistrationRepository;

/** Organizer export of all current registrations as an Excel workbook. */
@Service
public class ExportService {

  private final RegistrationRepository repository;
  private final RegistrationWorkbookWriter workbookWriter;

  public ExportService(
      RegistrationRepository repository, RegistrationWorkbookWriter workbookWriter) {
    this.repository = repository;
    this.workbookWriter = workbookWriter;
  }

  @Transactional(readOnly = true)
  public byte[] exportWorkbook() {
    try {
      return workbookWriter.write(repository.findAllByOrderByCreatedAtAsc());
    } catch (IOException e) {
      throw new UncheckedIOException("Could not generate registration export", e);
    }
  }
}
