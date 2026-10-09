package si.konferenca.registration.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use case: export all registrations as a workbook (US-008). */
@Service
public class ExportRegistrations {

  private final RegistrationStore store;
  private final WorkbookWriter writer;

  public ExportRegistrations(RegistrationStore store, WorkbookWriter writer) {
    this.store = store;
    this.writer = writer;
  }

  @Transactional(readOnly = true)
  public byte[] export() {
    return writer.write(store.findAll());
  }
}
