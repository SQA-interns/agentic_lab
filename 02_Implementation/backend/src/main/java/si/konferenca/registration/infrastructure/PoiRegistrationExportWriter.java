package si.konferenca.registration.infrastructure;

import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.service.RegistrationExportWriter;

/** Writes registrations to an XLSX workbook with Apache POI. All values are string cells. */
@Component
public class PoiRegistrationExportWriter implements RegistrationExportWriter {

  static final String SHEET_NAME = "Registrations";
  static final List<String> HEADERS =
      List.of(
          "Registration ID",
          "Submitted at (UTC)",
          "Type",
          "First name",
          "Last name",
          "Email",
          "Organization / institution",
          "Study institution",
          "Study programme",
          "Student ID",
          "Privacy consent",
          "Workshops",
          "Events",
          "Meals",
          "Other activities");

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
  private static final String FORMULA_TRIGGERS = "=+-@\t\r";

  @Override
  public void write(List<Registration> registrations, OutputStream out) throws IOException {
    try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
      Sheet sheet = workbook.createSheet(SHEET_NAME);
      writeHeader(workbook, sheet);
      int rowIndex = 1;
      for (Registration registration : registrations) {
        writeRow(sheet.createRow(rowIndex++), registration);
      }
      sheet.createFreezePane(0, 1);
      sheet.setAutoFilter(
          new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, HEADERS.size() - 1));
      workbook.write(out);
      workbook.dispose();
    }
  }

  private static void writeHeader(SXSSFWorkbook workbook, Sheet sheet) {
    Font bold = workbook.createFont();
    bold.setBold(true);
    CellStyle headerStyle = workbook.createCellStyle();
    headerStyle.setFont(bold);
    Row header = sheet.createRow(0);
    for (int i = 0; i < HEADERS.size(); i++) {
      header.createCell(i).setCellValue(HEADERS.get(i));
      header.getCell(i).setCellStyle(headerStyle);
      sheet.setColumnWidth(i, 22 * 256);
    }
  }

  private static void writeRow(Row row, Registration registration) {
    List<String> values =
        List.of(
            registration.getId().toString(),
            TIMESTAMP.format(registration.getCreatedAt()),
            RegistrationText.typeLabel(registration.getType()),
            nullToEmpty(registration.getFirstName()),
            nullToEmpty(registration.getLastName()),
            nullToEmpty(registration.getEmail()),
            nullToEmpty(registration.getOrganization()),
            nullToEmpty(registration.getStudyInstitution()),
            nullToEmpty(registration.getStudyProgramme()),
            nullToEmpty(registration.getStudentId()),
            registration.isPrivacyConsent() ? "Yes" : "No",
            options(registration, OptionCategory.WORKSHOP),
            options(registration, OptionCategory.EVENT),
            options(registration, OptionCategory.MEAL),
            options(registration, OptionCategory.OTHER));
    for (int i = 0; i < values.size(); i++) {
      row.createCell(i).setCellValue(neutralize(values.get(i)));
    }
  }

  private static String options(Registration registration, OptionCategory category) {
    return registration.getOptions().stream()
        .filter(o -> o.getCategory() == category)
        .map(o -> o.getOptionName() + " [" + o.getOptionId() + "]")
        .collect(Collectors.joining("; "));
  }

  /** Prevents spreadsheet formula injection when values are copied into other tools. */
  static String neutralize(String value) {
    if (!value.isEmpty() && FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0) {
      return "'" + value;
    }
    return value;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
