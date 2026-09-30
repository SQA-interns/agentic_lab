package lab.conference.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import lab.conference.registration.AcceptedRegistration;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Organizer workbook per docs/02_contracts/export-workbook.json, built in memory so no personal
 * data is written to temporary files. Every value is a string cell; values that a spreadsheet could
 * interpret as a formula get a quote prefix (SR-04).
 */
public final class WorkbookWriter {

  /** Column header and value extractor, in contract order. */
  record Column(String header, Function<AcceptedRegistration, String> value) {}

  static final List<Column> COLUMNS =
      List.of(
          new Column("Registration ID", r -> r.registrationId().toString()),
          new Column("Form type", AcceptedRegistration::formType),
          new Column("Accepted at (UTC)", r -> r.acceptedAt().toString()),
          new Column("First name", r -> r.participant().get("firstName")),
          new Column("Last name", r -> r.participant().get("lastName")),
          new Column("Email", r -> r.participant().get("email")),
          new Column("Organization / institution", r -> r.participant().get("organization")),
          new Column("Study institution", r -> r.participant().get("studyInstitution")),
          new Column("Study programme", r -> r.participant().get("studyProgramme")),
          new Column("Student ID", r -> r.participant().get("studentId")),
          new Column("Workshops", r -> names(r, "workshops")),
          new Column("Events", r -> names(r, "events")),
          new Column("Meals", r -> names(r, "meals")),
          new Column("Other activities", r -> names(r, "other")),
          new Column("Consent", WorkbookWriter::consent),
          new Column("Client request ID", r -> r.clientRequestId().toString()));

  private WorkbookWriter() {}

  public static byte[] write(List<AcceptedRegistration> registrations) throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      CellStyle quoted = wb.createCellStyle();
      quoted.setQuotePrefixed(true);
      Sheet sheet = wb.createSheet("Registrations");
      Row header = sheet.createRow(0);
      for (int c = 0; c < COLUMNS.size(); c++) {
        header.createCell(c).setCellValue(COLUMNS.get(c).header());
      }
      int rowIndex = 1;
      for (AcceptedRegistration r : registrations) {
        Row row = sheet.createRow(rowIndex++);
        for (int c = 0; c < COLUMNS.size(); c++) {
          String value = COLUMNS.get(c).value().apply(r);
          Cell cell = row.createCell(c);
          cell.setCellValue(value == null ? "" : value);
          if (needsQuotePrefix(value)) {
            cell.setCellStyle(quoted);
          }
        }
      }
      wb.write(out);
      return out.toByteArray();
    }
  }

  static boolean needsQuotePrefix(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    char first = value.charAt(0);
    return first == '='
        || first == '+'
        || first == '-'
        || first == '@'
        || first == '\t'
        || first == '\r';
  }

  private static String names(AcceptedRegistration r, String group) {
    return String.join("; ", r.selectionNames().getOrDefault(group, List.of()));
  }

  private static String consent(AcceptedRegistration r) {
    if (r.consentGiven() == null) {
      return "";
    }
    return r.consentGiven() ? "yes" : "no";
  }
}
