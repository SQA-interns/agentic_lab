package si.konferenca.registration.adapter.out.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationExporter;
import si.konferenca.registration.domain.TextField;

/**
 * Writes the export workbook with a streaming writer: one sheet, a heading row and one row per
 * registration with registration data only (SR-07). Every cell is text.
 */
public class PoiRegistrationExporter implements RegistrationExporter {

  private static final String SHEET_NAME = "Prijave";
  private static final List<String> HEADINGS =
      List.of(
          "Številka prijave",
          "Sprejeto",
          "Vrsta prijave",
          "Ime",
          "Priimek",
          "E-pošta",
          "Organizacija / ustanova",
          "Izobraževalna ustanova",
          "Študijski program",
          "Vpisna številka",
          "Delavnice",
          "Dogodki",
          "Obroki",
          "Druge aktivnosti",
          "Soglasje dano");
  private static final List<TextField> FIELD_COLUMNS =
      List.of(
          TextField.FIRST_NAME,
          TextField.LAST_NAME,
          TextField.EMAIL,
          TextField.ORGANIZATION,
          TextField.STUDY_INSTITUTION,
          TextField.STUDY_PROGRAMME,
          TextField.STUDENT_ID);
  private static final String FORMULA_TRIGGERS = "=+-@\t\r";

  @Override
  public byte[] export(List<Registration> registrations) {
    try (SXSSFWorkbook workbook = new SXSSFWorkbook();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet(SHEET_NAME);
      int rowIndex = 0;
      writeRow(sheet.createRow(rowIndex++), HEADINGS);
      for (Registration registration : registrations) {
        writeRow(sheet.createRow(rowIndex++), cells(registration));
      }
      workbook.write(output);
      return output.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("The workbook could not be written", e);
    }
  }

  private static List<String> cells(Registration registration) {
    List<String> cells = new ArrayList<>();
    cells.add(registration.id().toString());
    cells.add(registration.acceptedAt().toString());
    cells.add(registration.type().name());
    for (TextField field : FIELD_COLUMNS) {
      String value = registration.value(field);
      cells.add(value == null ? "" : value);
    }
    for (OptionCategory category : OptionCategory.values()) {
      cells.add(
          registration.options().stream()
              .filter(option -> option.category() == category)
              .map(Registration.SelectedOption::name)
              .collect(Collectors.joining("; ")));
    }
    cells.add(registration.consent().givenAt().toString());
    return cells;
  }

  private static void writeRow(Row row, List<String> values) {
    for (int column = 0; column < values.size(); column++) {
      row.createCell(column).setCellValue(neutralised(values.get(column)));
    }
  }

  /**
   * A value that a spreadsheet program could read as a formula gets a leading apostrophe, so
   * participant input is never evaluated (SB-05).
   */
  static String neutralised(String value) {
    if (!value.isEmpty() && FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0) {
      return "'" + value;
    }
    return value;
  }
}
