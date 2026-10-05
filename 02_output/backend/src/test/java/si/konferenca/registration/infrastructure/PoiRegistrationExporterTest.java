package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;

class PoiRegistrationExporterTest {

  static Registration external(String firstName) {
    return new Registration(
        UUID.randomUUID(),
        RegistrationType.EXTERNAL,
        new Participant(firstName, "Novak", "a@b.si", "IJS", null, null, null),
        List.of(
            new SelectedOption("ws-a", "Delavnica A", OptionCategory.WORKSHOP),
            new SelectedOption("meal-b", "Kosilo", OptionCategory.MEAL)),
        "c1",
        "Consent",
        Instant.parse("2026-10-05T10:00:00.120Z"));
  }

  @Test
  void writesHeadingsAndOneStringRowPerRegistration() throws Exception {
    Registration registration = external("=HYPERLINK(\"http://evil\")");
    byte[] bytes = new PoiRegistrationExporter().export(List.of(registration));

    try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = book.getSheet("Registrations");
      assertThat(sheet.getLastRowNum()).isEqualTo(1);
      Row head = sheet.getRow(0);
      for (int i = 0; i < PoiRegistrationExporter.HEADINGS.size(); i++) {
        assertThat(head.getCell(i).getStringCellValue())
            .isEqualTo(PoiRegistrationExporter.HEADINGS.get(i));
      }
      Row row = sheet.getRow(1);
      assertThat(row.getCell(0).getStringCellValue()).isEqualTo(registration.id().toString());
      assertThat(row.getCell(1).getStringCellValue()).isEqualTo("External participant");
      assertThat(row.getCell(2).getStringCellValue()).isEqualTo("2026-10-05T10:00:00.120Z");
      assertThat(row.getCell(3).getCellType()).isEqualTo(CellType.STRING);
      assertThat(row.getCell(3).getStringCellValue()).isEqualTo("=HYPERLINK(\"http://evil\")");
      assertThat(row.getCell(7)).isNull();
      assertThat(row.getCell(10).getStringCellValue())
          .isEqualTo("Delavnica A [ws-a]; Kosilo [meal-b]");
      assertThat(row.getCell(12).getStringCellValue()).isEqualTo("2026-10-05T10:00:00.120Z");
    }
  }

  @Test
  void emptyListGivesOnlyHeadings() throws Exception {
    byte[] bytes = new PoiRegistrationExporter().export(List.of());

    try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      assertThat(book.getSheet("Registrations").getLastRowNum()).isZero();
    }
  }
}
