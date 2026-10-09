package si.konferenca.registration.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ParticipantDetails;
import si.konferenca.registration.domain.Registration;

/**
 * The organizer export (US-008): one row per registration, oldest first, with only the columns of
 * docs/02_contracts/export-workbook.json (SR-07).
 */
public class ExportService {

  static final String SHEET_NAME = "Registrations";
  static final List<String> HEADER =
      List.of(
          "Registration ID",
          "Registered at",
          "Type",
          "First name",
          "Last name",
          "Email",
          "Organization",
          "Study institution",
          "Study programme",
          "Student ID",
          "Workshops",
          "Events",
          "Meals",
          "Other activities",
          "Consents");

  private static final Logger LOG = LoggerFactory.getLogger(ExportService.class);
  private static final String SEPARATOR = "; ";

  private final RegistrationRepository repository;
  private final WorkbookWriter writer;
  private final TransactionTemplate readOnlyTransactions;

  public ExportService(
      RegistrationRepository repository,
      WorkbookWriter writer,
      TransactionTemplate readOnlyTransactions) {
    this.repository = repository;
    this.writer = writer;
    this.readOnlyTransactions = readOnlyTransactions;
  }

  /** The workbook bytes (.xlsx). */
  public byte[] exportWorkbook() {
    List<List<String>> rows =
        Objects.requireNonNull(
            readOnlyTransactions.execute(
                status ->
                    repository.findAllOldestFirst().stream().map(ExportService::row).toList()));
    LOG.info("Export of {} registrations", rows.size());
    return writer.write(SHEET_NAME, HEADER, rows);
  }

  private static List<String> row(Registration registration) {
    ParticipantDetails participant = registration.participant();
    List<String> row = new ArrayList<>();
    row.add(registration.id().toString());
    row.add(registration.registeredAt().toString());
    row.add(registration.type().label());
    row.add(participant.firstName());
    row.add(participant.lastName());
    row.add(participant.email());
    row.add(orEmpty(participant.organization()));
    row.add(orEmpty(participant.studyInstitution()));
    row.add(orEmpty(participant.studyProgramme()));
    row.add(orEmpty(participant.studentId()));
    for (Category category : Category.values()) {
      row.add(
          registration.options().stream()
              .filter(option -> option.category() == category)
              .map(option -> option.optionName())
              .collect(Collectors.joining(SEPARATOR)));
    }
    row.add(
        registration.consents().stream()
            .map(consent -> consent.consentId() + " (" + consent.givenAt() + ")")
            .collect(Collectors.joining(SEPARATOR)));
    return row;
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }
}
