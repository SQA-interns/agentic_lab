package si.konferenca.registration.infrastructure.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import si.konferenca.registration.application.port.RegistrationWorkbookWriter;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.SelectedOption;

/** Writes registrations to an .xlsx workbook. All values are string cells, never formulas. */
@Component
public class PoiRegistrationWorkbookWriter implements RegistrationWorkbookWriter {

  static final String SHEET_NAME = "Registrations";

  static final List<String> HEADERS =
      List.of(
          "Registration ID",
          "Registered at (UTC)",
          "Type",
          "First name",
          "Last name",
          "Email",
          "Organization / institution",
          "Study institution",
          "Study programme",
          "Student ID",
          "Workshops",
          "Events",
          "Meals",
          "Other activities",
          "Consents");

  @Override
  public byte[] write(List<Registration> registrations) throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet(SHEET_NAME);
      writeHeader(workbook, sheet);
      int rowIndex = 1;
      for (Registration registration : registrations) {
        writeRow(sheet.createRow(rowIndex++), registration);
      }
      sheet.createFreezePane(0, 1);
      for (int column = 0; column < HEADERS.size(); column++) {
        sheet.setColumnWidth(column, 24 * 256);
      }
      workbook.write(out);
      return out.toByteArray();
    }
  }

  private static void writeHeader(XSSFWorkbook workbook, Sheet sheet) {
    Font bold = workbook.createFont();
    bold.setBold(true);
    CellStyle style = workbook.createCellStyle();
    style.setFont(bold);
    Row header = sheet.createRow(0);
    for (int column = 0; column < HEADERS.size(); column++) {
      header.createCell(column).setCellValue(HEADERS.get(column));
      header.getCell(column).setCellStyle(style);
    }
  }

  private static void writeRow(Row row, Registration r) {
    List<String> values =
        List.of(
            String.valueOf(r.getId()),
            DateTimeFormatter.ISO_INSTANT.format(r.getCreatedAt()),
            r.getType().label(),
            nullToEmpty(r.getFirstName()),
            nullToEmpty(r.getLastName()),
            nullToEmpty(r.getEmail()),
            nullToEmpty(r.getOrganization()),
            nullToEmpty(r.getStudyInstitution()),
            nullToEmpty(r.getStudyProgramme()),
            nullToEmpty(r.getStudentId()),
            options(r, OptionCategory.WORKSHOP),
            options(r, OptionCategory.EVENT),
            options(r, OptionCategory.MEAL),
            options(r, OptionCategory.OTHER),
            String.join("; ", r.getConsentIds()));
    for (int column = 0; column < values.size(); column++) {
      row.createCell(column).setCellValue(values.get(column));
    }
  }

  private static String options(Registration registration, OptionCategory category) {
    return registration.getSelectedOptions(category).stream()
        .map(SelectedOption::getOptionName)
        .collect(Collectors.joining("; "));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
