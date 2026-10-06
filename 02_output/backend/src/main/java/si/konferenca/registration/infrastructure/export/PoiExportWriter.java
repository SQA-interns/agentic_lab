package si.konferenca.registration.infrastructure.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import si.konferenca.registration.application.RegistrationPorts.ExportWriter;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;

/**
 * Excel workbook of docs/02_specification.md §7: sheet "Registrations", a fixed column list (SR-07)
 * and only string cells, so no value is ever evaluated as a formula.
 */
public class PoiExportWriter implements ExportWriter {

  static final List<String> COLUMNS =
      List.of(
          "Registration ID",
          "Received at",
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
  public byte[] write(List<Registration> registrations) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Registrations");
      writeRow(sheet.createRow(0), COLUMNS);
      int index = 1;
      for (Registration registration : registrations) {
        writeRow(sheet.createRow(index++), cells(registration));
      }
      sheet.createFreezePane(0, 1);
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("export workbook could not be written", e);
    }
  }

  private static void writeRow(Row row, List<String> values) {
    for (int i = 0; i < values.size(); i++) {
      row.createCell(i).setCellValue(values.get(i));
    }
  }

  static List<String> cells(Registration r) {
    Participant p = r.participant();
    List<String> cells = new ArrayList<>();
    cells.add(r.id().toString());
    cells.add(r.receivedAt().toString());
    cells.add(r.type().value());
    cells.add(p.firstName());
    cells.add(p.lastName());
    cells.add(p.email());
    cells.add(orEmpty(p.organization()));
    cells.add(orEmpty(p.studyInstitution()));
    cells.add(orEmpty(p.studyProgramme()));
    cells.add(orEmpty(p.studentId()));
    for (OptionCategory category : OptionCategory.values()) {
      cells.add(String.join("; ", r.optionNames(category)));
    }
    cells.add(
        String.join(
            "; ", r.consents().stream().map(c -> c.id() + " (" + c.givenAt() + ")").toList()));
    return cells;
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }
}
