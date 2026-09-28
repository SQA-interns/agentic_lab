package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.SelectedOption;

class PoiRegistrationExportWriterTest {

  private final PoiRegistrationExportWriter writer = new PoiRegistrationExportWriter();

  private XSSFWorkbook export(List<Registration> registrations) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writer.write(registrations, out);
    return new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()));
  }

  @Test
  void writesHeaderOnlyForEmptyList() throws Exception { // AC-008-06
    try (XSSFWorkbook workbook = export(List.of())) {
      Sheet sheet = workbook.getSheet("Registrations");
      assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);
      Row header = sheet.getRow(0);
      for (int i = 0; i < PoiRegistrationExportWriter.HEADERS.size(); i++) {
        assertThat(header.getCell(i).getStringCellValue())
            .isEqualTo(PoiRegistrationExportWriter.HEADERS.get(i));
      }
    }
  }

  @Test
  void writesOneRowPerRegistrationWithOptionsPerCategory() throws Exception { // AC-008-02
    Registration external =
        Registration.external(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            new Registration.ParticipantName("Ana", "Novak"),
            "ana@example.si",
            "Institut Jožef Stefan",
            true,
            Instant.parse("2026-02-03T04:05:06Z"),
            List.of(
                new SelectedOption("ws-1", OptionCategory.WORKSHOP, "Delavnica 1"),
                new SelectedOption("ws-2", OptionCategory.WORKSHOP, "Delavnica 2"),
                new SelectedOption("other-1", OptionCategory.OTHER, "Izlet")));
    Registration student =
        Registration.student(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            new Registration.ParticipantName("Žiga", "Čeh"),
            "ziga@example.si",
            new Registration.StudentDetails("Univerza v Mariboru", "Informatika", "E1234"),
            true,
            Instant.parse("2026-02-03T05:00:00Z"),
            List.of(new SelectedOption("meal-1", OptionCategory.MEAL, "Kosilo")));

    try (XSSFWorkbook workbook = export(List.of(external, student))) {
      Sheet sheet = workbook.getSheet("Registrations");
      Row first = sheet.getRow(1);
      assertThat(first.getCell(0).getStringCellValue())
          .isEqualTo("00000000-0000-0000-0000-000000000001");
      assertThat(first.getCell(1).getStringCellValue()).isEqualTo("2026-02-03 04:05:06");
      assertThat(first.getCell(2).getStringCellValue()).isEqualTo("External participant");
      assertThat(first.getCell(6).getStringCellValue()).isEqualTo("Institut Jožef Stefan");
      assertThat(first.getCell(9).getStringCellValue()).isEmpty();
      assertThat(first.getCell(10).getStringCellValue()).isEqualTo("Yes");
      assertThat(first.getCell(11).getStringCellValue())
          .isEqualTo("Delavnica 1 [ws-1]; Delavnica 2 [ws-2]");
      assertThat(first.getCell(12).getStringCellValue()).isEmpty();
      assertThat(first.getCell(14).getStringCellValue()).isEqualTo("Izlet [other-1]");

      Row second = sheet.getRow(2);
      assertThat(second.getCell(2).getStringCellValue()).isEqualTo("Student");
      assertThat(second.getCell(3).getStringCellValue()).isEqualTo("Žiga");
      assertThat(second.getCell(4).getStringCellValue()).isEqualTo("Čeh");
      assertThat(second.getCell(7).getStringCellValue()).isEqualTo("Univerza v Mariboru");
      assertThat(second.getCell(8).getStringCellValue()).isEqualTo("Informatika");
      assertThat(second.getCell(9).getStringCellValue()).isEqualTo("E1234");
      assertThat(second.getCell(13).getStringCellValue()).isEqualTo("Kosilo [meal-1]");
    }
  }

  @Test
  void neutralizesFormulaInjection() throws Exception { // spec §7.2
    Registration registration =
        Registration.external(
            UUID.randomUUID(),
            new Registration.ParticipantName("=HYPERLINK(\"http://evil\")", "+SUM(A1)"),
            "a@example.si",
            "@cmd",
            true,
            Instant.now(),
            List.of());

    try (XSSFWorkbook workbook = export(List.of(registration))) {
      Row row = workbook.getSheet("Registrations").getRow(1);
      assertThat(row.getCell(3).getCellType()).isEqualTo(CellType.STRING);
      assertThat(row.getCell(3).getStringCellValue()).isEqualTo("'=HYPERLINK(\"http://evil\")");
      assertThat(row.getCell(4).getStringCellValue()).isEqualTo("'+SUM(A1)");
      assertThat(row.getCell(6).getStringCellValue()).isEqualTo("'@cmd");
    }
  }

  @Test
  void neutralizeLeavesOrdinaryValuesUnchanged() {
    assertThat(PoiRegistrationExportWriter.neutralize("Ana")).isEqualTo("Ana");
    assertThat(PoiRegistrationExportWriter.neutralize("")).isEmpty();
    assertThat(PoiRegistrationExportWriter.neutralize("-1")).isEqualTo("'-1");
  }
}
