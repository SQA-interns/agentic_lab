package si.konferenca.registration.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import si.konferenca.registration.infrastructure.ExcelWriter;
import si.konferenca.registration.persistence.RegistrationStore;

/** The organizer export of all current registrations (US-008). */
@Service
public class ExportService {

  private final RegistrationStore store;
  private final ExcelWriter writer;

  public ExportService(RegistrationStore store, ExcelWriter writer) {
    this.store = store;
    this.writer = writer;
  }

  @Transactional(readOnly = true)
  public byte[] workbook() {
    try {
      return writer.write(store.findAllOrderedByReceivedAt());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
