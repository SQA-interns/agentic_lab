package si.konferenca.registration.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import si.konferenca.registration.application.RegistrationCopy;
import si.konferenca.registration.application.WorkbookWriter;

/**
 * Writes the export workbook with Apache POI (02_specification.md 5.4). Every cell is a string
 * cell, so input that looks like a formula is never evaluated. Only registration data is written
 * (SR-07).
 */
@Component
public class PoiWorkbookWriter implements WorkbookWriter {

  static final List<String> HEADER =
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
          "Consents");

  @Override
  public byte[] write(List<RegistrationCopy> registrations) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Registrations");
      writeRow(sheet.createRow(0), HEADER);
      int index = 1;
      for (RegistrationCopy r : registrations) {
        writeRow(sheet.createRow(index++), cells(r));
      }
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Export workbook could not be written", e);
    }
  }

  static List<String> cells(RegistrationCopy r) {
    return List.of(
        r.id().toString(),
        r.submittedAt().toString(),
        r.type(),
        r.firstName(),
        r.lastName(),
        r.email(),
        orEmpty(r.organization()),
        orEmpty(r.studyInstitution()),
        orEmpty(r.studyProgramme()),
        orEmpty(r.studentId()),
        names(r, "workshop"),
        names(r, "event"),
        names(r, "meal"),
        names(r, "other"),
        r.consents().stream()
            .map(c -> c.id() + " (" + c.givenAt() + ")")
            .collect(Collectors.joining("; ")));
  }

  private static String names(RegistrationCopy r, String category) {
    return r.options().stream()
        .filter(o -> category.equals(o.category()))
        .map(RegistrationCopy.Option::name)
        .collect(Collectors.joining("; "));
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }

  private static void writeRow(Row row, List<String> values) {
    for (int i = 0; i < values.size(); i++) {
      row.createCell(i).setCellValue(values.get(i));
    }
  }
}
