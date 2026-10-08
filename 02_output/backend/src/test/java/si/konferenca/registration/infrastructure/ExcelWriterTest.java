package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.service.NotificationServiceTest;

class ExcelWriterTest {

  private final ExcelWriter writer = new ExcelWriter();

  private static List<List<String>> rows(byte[] xlsx) throws Exception {
    try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = book.getSheet("Registrations");
      List<List<String>> rows = new ArrayList<>();
      for (Row row : sheet) {
        List<String> cells = new ArrayList<>();
        for (Cell cell : row) {
          assertThat(cell.getCellType()).isEqualTo(CellType.STRING);
          cells.add(cell.getStringCellValue());
        }
        rows.add(cells);
      }
      return rows;
    }
  }

  @Test
  @DisplayName("US-008 header row as specified, even with no registrations")
  void headerOnlyWhenEmpty() throws Exception {
    List<List<String>> rows = rows(writer.write(List.of()));
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsExactlyElementsOf(ExcelWriter.HEADERS);
  }

  @Test
  @DisplayName("US-008 one row per registration with options grouped per category")
  void studentRow() throws Exception {
    Registration r = NotificationServiceTest.student();
    List<String> row = rows(writer.write(List.of(r))).get(1);
    assertThat(row)
        .containsExactly(
            r.id().toString(),
            "STUDENT",
            "2026-10-08T10:00:00Z",
            "Žiga",
            "Kovač",
            "ziga@example.si",
            "",
            "Uni LJ",
            "RI",
            "6321",
            "Delavnica",
            "",
            "Lunch; Dinner",
            "",
            "data @ 2026-10-08T10:00:00Z");
  }

  @Test
  @DisplayName("SR-07 text that looks like a formula stays text")
  void formulaLikeTextStaysText() throws Exception {
    Registration base = NotificationServiceTest.student();
    Registration r =
        new Registration(
            UUID.randomUUID(),
            base.receivedAt(),
            RegistrationType.EXTERNAL,
            new Participant(
                "=HYPERLINK(\"http://x\")", "+1", "a@b.si", "@SUM(A1)", null, null, null),
            List.of(),
            base.consents());
    List<String> row = rows(writer.write(List.of(r))).get(1);
    assertThat(row.get(3)).isEqualTo("=HYPERLINK(\"http://x\")");
    assertThat(row.get(6)).isEqualTo("@SUM(A1)");
  }
}
