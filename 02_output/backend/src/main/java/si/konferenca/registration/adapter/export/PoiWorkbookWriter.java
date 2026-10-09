package si.konferenca.registration.adapter.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import si.konferenca.registration.application.WorkbookWriter;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.Field;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.Registration;

/**
 * Sheet "Registrations" (docs/02_specification.md section 7). Every cell is a string cell, so no
 * participant input is ever evaluated as a formula (SB-05); only registration data (SR-07).
 */
@Component
public class PoiWorkbookWriter implements WorkbookWriter {

  static final String SHEET = "Registrations";
  static final String SEPARATOR = "; ";

  @Override
  public byte[] write(List<Registration> registrations) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet(SHEET);
      writeRow(sheet.createRow(0), headings());
      int index = 1;
      for (Registration registration : registrations) {
        writeRow(sheet.createRow(index++), cells(registration));
      }
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static List<String> headings() {
    List<String> headings = new ArrayList<>();
    headings.add("Registration ID");
    headings.add("Type");
    for (Field field : Field.values()) {
      headings.add(field.label());
    }
    for (Category category : Category.values()) {
      headings.add(category.heading());
    }
    headings.add("Consents");
    headings.add("Registered at");
    return headings;
  }

  static List<String> cells(Registration registration) {
    List<String> cells = new ArrayList<>();
    cells.add(registration.id().toString());
    cells.add(registration.type().label());
    for (Field field : Field.values()) {
      String value = registration.value(field);
      cells.add(value == null ? "" : value);
    }
    for (Category category : Category.values()) {
      cells.add(String.join(SEPARATOR, registration.optionNames(category)));
    }
    cells.add(
        String.join(SEPARATOR, registration.consents().stream().map(GivenConsent::id).toList()));
    cells.add(registration.submittedAt().truncatedTo(ChronoUnit.MILLIS).toString());
    return cells;
  }

  private static void writeRow(Row row, List<String> values) {
    for (int i = 0; i < values.size(); i++) {
      row.createCell(i).setCellValue(values.get(i));
    }
  }
}
