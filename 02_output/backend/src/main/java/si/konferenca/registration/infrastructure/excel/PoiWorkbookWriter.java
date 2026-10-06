package si.konferenca.registration.infrastructure.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import si.konferenca.registration.application.WorkbookWriter;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;

/**
 * Excel export with an explicit column list (SR-07). Every cell is a string cell, so values that
 * look like formulas stay text (AC-008-06).
 */
public class PoiWorkbookWriter implements WorkbookWriter {

  private record Column(String header, Function<Registration, String> value) {}

  private static final List<Column> COLUMNS = columns();

  private static List<Column> columns() {
    List<Column> c = new ArrayList<>();
    c.add(new Column("Registration ID", r -> r.id().toString()));
    c.add(new Column("Received at (UTC)", r -> r.receivedAt().toString()));
    c.add(new Column("Type", r -> r.type().name()));
    c.add(new Column("First name", Registration::firstName));
    c.add(new Column("Last name", Registration::lastName));
    c.add(new Column("Email", Registration::email));
    c.add(new Column("Organization / institution", Registration::organization));
    c.add(new Column("Study institution", Registration::studyInstitution));
    c.add(new Column("Study programme", Registration::studyProgramme));
    c.add(new Column("Student ID", Registration::studentId));
    for (OptionCategory category : OptionCategory.values()) {
      c.add(
          new Column(
              category.label(),
              r ->
                  r.optionsIn(category).stream()
                      .map(ConferenceOption::name)
                      .collect(Collectors.joining("; "))));
    }
    c.add(new Column("Consent", r -> r.consent().text()));
    c.add(new Column("Consent given at (UTC)", r -> r.receivedAt().toString()));
    return List.copyOf(c);
  }

  @Override
  public byte[] write(List<Registration> registrations) {
    try (XSSFWorkbook wb = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XSSFSheet sheet = wb.createSheet("Registrations");
      XSSFCellStyle bold = wb.createCellStyle();
      Font font = wb.createFont();
      font.setBold(true);
      bold.setFont(font);
      XSSFRow header = sheet.createRow(0);
      for (int i = 0; i < COLUMNS.size(); i++) {
        header.createCell(i).setCellValue(COLUMNS.get(i).header());
        header.getCell(i).setCellStyle(bold);
        sheet.setColumnWidth(i, 24 * 256);
      }
      int rowIndex = 1;
      for (Registration r : registrations) {
        XSSFRow row = sheet.createRow(rowIndex++);
        for (int i = 0; i < COLUMNS.size(); i++) {
          String value = COLUMNS.get(i).value().apply(r);
          row.createCell(i).setCellValue(value == null ? "" : value);
        }
      }
      sheet.createFreezePane(0, 1);
      wb.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Workbook could not be written", e);
    }
  }
}
