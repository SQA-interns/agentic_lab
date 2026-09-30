package si.konferenca.registration.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import si.konferenca.registration.domain.RegistrationRepository;

/** Builds the organizer export of all registrations (US-008). */
@Service
public class ExportService {

  private final RegistrationRepository repository;
  private final WorkbookWriter writer;

  public ExportService(RegistrationRepository repository, WorkbookWriter writer) {
    this.repository = repository;
    this.writer = writer;
  }

  @Transactional(readOnly = true)
  public byte[] exportWorkbook() {
    return writer.write(
        repository.findAllByOrderBySubmittedAtAsc().stream().map(RegistrationCopy::of).toList());
  }
}
