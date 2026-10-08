package si.konferenca.registration.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;

/**
 * Writes the organizer export (specification section 6). Every cell is a string cell, so no value
 * is ever evaluated as a formula; only registration data is written (SR-07).
 */
@Component
public class ExcelWriter {

  static final List<String> HEADERS =
      List.of(
          "Registration ID",
          "Type",
          "Received at",
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

  public byte[] write(List<Registration> registrations) throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Registrations");
      writeRow(sheet.createRow(0), HEADERS);
      int index = 1;
      for (Registration r : registrations) {
        writeRow(sheet.createRow(index++), values(r));
      }
      workbook.write(out);
      return out.toByteArray();
    }
  }

  private static List<String> values(Registration r) {
    Participant p = r.participant();
    return List.of(
        r.id().toString(),
        r.type().name(),
        r.receivedAt().toString(),
        p.firstName(),
        p.lastName(),
        p.email(),
        orEmpty(p.organization()),
        orEmpty(p.studyInstitution()),
        orEmpty(p.studyProgramme()),
        orEmpty(p.studentId()),
        options(r, Category.WORKSHOP),
        options(r, Category.EVENT),
        options(r, Category.MEAL),
        options(r, Category.OTHER),
        r.consents().stream()
            .map(c -> c.id() + " @ " + c.givenAt())
            .collect(Collectors.joining("; ")));
  }

  private static String options(Registration r, Category category) {
    return r.options().stream()
        .filter(o -> o.category() == category)
        .map(o -> o.name())
        .collect(Collectors.joining("; "));
  }

  private static void writeRow(Row row, List<String> values) {
    for (int i = 0; i < values.size(); i++) {
      row.createCell(i).setCellValue(values.get(i));
    }
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }
}
