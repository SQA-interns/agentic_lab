package si.konferenca.registration.infrastructure.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import si.konferenca.registration.TestFixtures;
import si.konferenca.registration.domain.Registration;

class PoiRegistrationWorkbookWriterTest {

  private final PoiRegistrationWorkbookWriter writer = new PoiRegistrationWorkbookWriter();

  private static List<String> rowValues(Row row) {
    List<String> values = new ArrayList<>();
    for (int i = 0; i < PoiRegistrationWorkbookWriter.HEADERS.size(); i++) {
      Cell cell = row.getCell(i);
      values.add(cell == null ? "" : cell.getStringCellValue());
    }
    return values;
  }

  @Test
  void emptyExportContainsOnlyHeaders() throws IOException {
    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(writer.write(List.of())))) {
      Sheet sheet = workbook.getSheet("Registrations");
      assertThat(sheet).isNotNull();
      assertThat(sheet.getLastRowNum()).isZero();
      assertThat(rowValues(sheet.getRow(0))).isEqualTo(PoiRegistrationWorkbookWriter.HEADERS);
    }
  }

  @Test
  void writesOneRowPerRegistrationWithTypeSpecificFieldsAndOptions() throws IOException {
    Registration external = TestFixtures.externalRegistration();
    Registration student = TestFixtures.studentRegistration();
    UUID externalId = UUID.randomUUID();
    ReflectionTestUtils.setField(external, "id", externalId);
    ReflectionTestUtils.setField(student, "id", UUID.randomUUID());

    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(writer.write(List.of(external, student))))) {
      Sheet sheet = workbook.getSheet("Registrations");
      assertThat(sheet.getLastRowNum()).isEqualTo(2);

      List<String> first = rowValues(sheet.getRow(1));
      assertThat(first)
          .containsExactly(
              externalId.toString(),
              "2026-03-01T10:15:30Z",
              "External participant",
              "Žiga",
              "Šušteršič",
              "ziga@example.si",
              "Univerza v Ljubljani — FRI",
              "",
              "",
              "",
              "Workshop A",
              "",
              "Lunch",
              "",
              "privacy");

      List<String> second = rowValues(sheet.getRow(2));
      assertThat(second.get(2)).isEqualTo("Student");
      assertThat(second.get(4)).isEqualTo("Čeč");
      assertThat(second.get(6)).isEmpty();
      assertThat(second.subList(7, 10))
          .containsExactly(
              "Fakulteta za računalništvo", "Računalništvo in informatika", "63210000");
    }
  }

  @Test
  void formulaLikeValuesAreStoredAsText() throws IOException {
    Registration registration =
        Registration.builder()
            .type(si.konferenca.registration.domain.RegistrationType.EXTERNAL)
            .firstName("=HYPERLINK(\"http://evil\")")
            .lastName("+1")
            .email("a@example.si")
            .organization("@SUM(A1)")
            .createdAt(java.time.Instant.parse("2026-03-01T00:00:00Z"))
            .build();
    ReflectionTestUtils.setField(registration, "id", UUID.randomUUID());
    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(writer.write(List.of(registration))))) {
      Row row = workbook.getSheetAt(0).getRow(1);
      assertThat(row.getCell(3).getCellType()).isEqualTo(CellType.STRING);
      assertThat(row.getCell(3).getStringCellValue()).isEqualTo("=HYPERLINK(\"http://evil\")");
      assertThat(row.getCell(6).getCellType()).isEqualTo(CellType.STRING);
    }
  }
}
