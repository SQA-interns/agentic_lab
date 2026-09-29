package si.konferenca.registration.integration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;

/**
 * Writes registrations to an .xlsx workbook (specification §7). Every value is a string cell, so
 * participant text is never interpreted as a formula.
 */
public class ExcelExportWriter {

  public static final String SHEET_NAME = "Registrations";

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
          "Workshops",
          "Events",
          "Meals",
          "Other activities",
          "Personal data consent at (UTC)");

  private static final String OPTION_SEPARATOR = "; ";

  public byte[] write(List<Registration> registrations) throws IOException {
    SXSSFWorkbook workbook = new SXSSFWorkbook(100);
    try {
      Sheet sheet = workbook.createSheet(SHEET_NAME);
      writeHeader(workbook, sheet);
      int rowIndex = 1;
      for (Registration r : registrations) {
        writeRow(sheet.createRow(rowIndex++), r);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      return out.toByteArray();
    } finally {
      workbook.close();
    }
  }

  private static void writeHeader(SXSSFWorkbook workbook, Sheet sheet) {
    Font bold = workbook.createFont();
    bold.setBold(true);
    CellStyle style = workbook.createCellStyle();
    style.setFont(bold);
    Row header = sheet.createRow(0);
    for (int i = 0; i < HEADERS.size(); i++) {
      Cell cell = header.createCell(i);
      cell.setCellValue(HEADERS.get(i));
      cell.setCellStyle(style);
    }
  }

  private static void writeRow(Row row, Registration r) {
    String[] values = {
      r.getId().toString(),
      r.getSubmittedAt().toString(),
      r.getType().shortLabel(),
      r.getFirstName(),
      r.getLastName(),
      r.getEmail(),
      r.getOrganization(),
      r.getStudyInstitution(),
      r.getStudyProgramme(),
      r.getStudentId(),
      optionNames(r, OptionCategory.WORKSHOP),
      optionNames(r, OptionCategory.EVENT),
      optionNames(r, OptionCategory.MEAL),
      optionNames(r, OptionCategory.OTHER),
      r.getPersonalDataConsentAt().toString()
    };
    for (int i = 0; i < values.length; i++) {
      if (values[i] != null && !values[i].isEmpty()) {
        row.createCell(i).setCellValue(values[i]);
      }
    }
  }

  private static String optionNames(Registration r, OptionCategory category) {
    return r.getOptions().stream()
        .filter(o -> o.getCategory() == category)
        .map(o -> o.getName())
        .collect(Collectors.joining(OPTION_SEPARATOR));
  }
}
