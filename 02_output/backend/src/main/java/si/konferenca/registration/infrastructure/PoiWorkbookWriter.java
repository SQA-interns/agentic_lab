package si.konferenca.registration.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import si.konferenca.registration.application.WorkbookWriter;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationOption;

/**
 * Excel export (specification section 9): registration data only (SR-07), every value a string cell
 * so nothing is evaluated as a formula.
 */
public class PoiWorkbookWriter implements WorkbookWriter {

  static final List<String> COLUMNS =
      List.of(
          "Reference",
          "Submitted at",
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
          "Other",
          "Consents");

  @Override
  public byte[] write(List<Registration> registrations) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Registrations");
      writeRow(sheet.createRow(0), COLUMNS);
      int index = 1;
      for (Registration r : registrations) {
        writeRow(sheet.createRow(index++), values(r));
      }
      for (int c = 0; c < COLUMNS.size(); c++) {
        sheet.setColumnWidth(c, 24 * 256);
      }
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("workbook could not be written", e);
    }
  }

  static List<String> values(Registration r) {
    return List.of(
        r.reference().toString(),
        r.submittedAt().toString(),
        r.type().name(),
        r.firstName(),
        r.lastName(),
        r.email(),
        orEmpty(r.organization()),
        orEmpty(r.studyInstitution()),
        orEmpty(r.studyProgramme()),
        orEmpty(r.studentId()),
        options(r, Category.WORKSHOP),
        options(r, Category.EVENT),
        options(r, Category.MEAL),
        options(r, Category.OTHER),
        r.consents().stream()
            .map(c -> c.consentId() + " (" + c.givenAt() + ")")
            .collect(Collectors.joining("; ")));
  }

  private static void writeRow(Row row, List<String> values) {
    for (int c = 0; c < values.size(); c++) {
      row.createCell(c).setCellValue(values.get(c));
    }
  }

  private static String options(Registration r, Category category) {
    return r.options().stream()
        .filter(o -> o.category() == category)
        .map(RegistrationOption::optionName)
        .collect(Collectors.joining("; "));
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }
}
