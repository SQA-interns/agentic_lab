package si.konferenca.registration.application;

import org.springframework.transaction.support.TransactionTemplate;

/** Builds the organizer export of all current registrations (US-008, BR-08). */
public class ExportService {

  private final RegistrationStore store;
  private final WorkbookWriter writer;
  private final TransactionTemplate transaction;

  public ExportService(
      RegistrationStore store, WorkbookWriter writer, TransactionTemplate transaction) {
    this.store = store;
    this.writer = writer;
    this.transaction = transaction;
  }

  public byte[] export() {
    return writer.write(transaction.execute(s -> store.findAll()));
  }
}
