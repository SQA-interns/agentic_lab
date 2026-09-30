package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.ParticipantDetails;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

class PoiWorkbookWriterTest {

  private static ConferenceOption option(String id, String name, Category category) {
    return new ConferenceOption(id, name, category, true, EnumSet.allOf(RegistrationType.class));
  }

  private static Registration external(String firstName) {
    return Registration.accept(
        UUID.randomUUID(),
        new ParticipantDetails(
            RegistrationType.EXTERNAL, firstName, "Novak", "a@example.si", "IJS", null, null, null),
        List.of(
            option("w1", "W one", Category.WORKSHOP),
            option("w2", "W two", Category.WORKSHOP),
            option("e", "Event", Category.EVENT),
            option("m", "Meal", Category.MEAL),
            option("o", "Tour", Category.OTHER)),
        List.of(new ConsentDefinition("dp", "t", true), new ConsentDefinition("ph", "p", false)),
        Instant.parse("2026-09-30T10:00:00Z"));
  }

  @Test
  void writesHeaderAndOneStringRowPerRegistration() throws IOException {
    Registration formulaLike = external("=HYPERLINK(\"http://x\")");
    byte[] bytes = new PoiWorkbookWriter().write(List.of(external("Ana"), formulaLike));

    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = wb.getSheet("Registrations");
      assertThat(sheet.getLastRowNum()).isEqualTo(2);
      Row header = sheet.getRow(0);
      for (int c = 0; c < PoiWorkbookWriter.COLUMNS.size(); c++) {
        assertThat(header.getCell(c).getStringCellValue())
            .isEqualTo(PoiWorkbookWriter.COLUMNS.get(c));
      }
      Row row = sheet.getRow(1);
      assertThat(row.getCell(2).getStringCellValue()).isEqualTo("EXTERNAL");
      assertThat(row.getCell(1).getStringCellValue()).isEqualTo("2026-09-30T10:00:00Z");
      assertThat(row.getCell(7).getStringCellValue()).isEmpty();
      assertThat(row.getCell(10).getStringCellValue()).isEqualTo("W one; W two");
      assertThat(row.getCell(11).getStringCellValue()).isEqualTo("Event");
      assertThat(row.getCell(12).getStringCellValue()).isEqualTo("Meal");
      assertThat(row.getCell(13).getStringCellValue()).isEqualTo("Tour");
      assertThat(row.getCell(14).getStringCellValue())
          .isEqualTo("dp (2026-09-30T10:00:00Z); ph (2026-09-30T10:00:00Z)");
      assertThat(sheet.getRow(2).getCell(3).getCellType()).isEqualTo(CellType.STRING);
      assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).startsWith("=HYPERLINK");
    }
  }

  @Test
  void emptyListGivesHeaderOnly() throws IOException {
    try (XSSFWorkbook wb =
        new XSSFWorkbook(new ByteArrayInputStream(new PoiWorkbookWriter().write(List.of())))) {
      assertThat(wb.getSheet("Registrations").getLastRowNum()).isZero();
    }
  }
}
