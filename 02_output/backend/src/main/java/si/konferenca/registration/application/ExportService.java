package si.konferenca.registration.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Produces the organizer export (US-008). */
@Service
public class ExportService {

  private final RegistrationStore store;
  private final WorkbookWriter writer;

  public ExportService(RegistrationStore store, WorkbookWriter writer) {
    this.store = store;
    this.writer = writer;
  }

  @Transactional(readOnly = true)
  public byte[] exportWorkbook() {
    return writer.write(store.findAllOldestFirst());
  }
}
