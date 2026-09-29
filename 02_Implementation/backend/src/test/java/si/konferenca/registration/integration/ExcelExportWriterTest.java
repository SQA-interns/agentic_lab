package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

class ExcelExportWriterTest {

  private final ExcelExportWriter writer = new ExcelExportWriter();

  private static Registration external(String organization, List<ConferenceOption> options) {
    return new Registration(
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        RegistrationType.EXTERNAL,
        new Registration.Participant("Ana", "Novak", "a@x.si", organization, null, null, null),
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:01Z"),
        options);
  }

  private static Sheet sheetOf(byte[] xlsx) throws IOException {
    return new XSSFWorkbook(new ByteArrayInputStream(xlsx)).getSheet("Registrations");
  }

  @Test
  void headerOnlyWhenEmpty() throws IOException {
    Sheet sheet = sheetOf(writer.write(List.of()));

    assertThat(sheet.getLastRowNum()).isZero();
    assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Registration ID");
    assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 15);
  }

  @Test
  void optionsAreGroupedPerCategoryInDisplayOrder() throws IOException {
    ConferenceOption w2 = new ConferenceOption("w2", OptionCategory.WORKSHOP, "Second", true, 2);
    ConferenceOption w1 = new ConferenceOption("w1", OptionCategory.WORKSHOP, "First", true, 1);
    ConferenceOption other = new ConferenceOption("o", OptionCategory.OTHER, "Tour", false, 0);

    Row row = sheetOf(writer.write(List.of(external("IJS", List.of(w2, other, w1))))).getRow(1);

    assertThat(row.getCell(10).getStringCellValue()).isEqualTo("First; Second");
    assertThat(row.getCell(11)).isNull();
    assertThat(row.getCell(13).getStringCellValue()).isEqualTo("Tour");
    assertThat(row.getCell(2).getStringCellValue()).isEqualTo("External");
  }

  @Test
  void formulaLikeTextStaysAString() throws IOException {
    Row row = sheetOf(writer.write(List.of(external("=1+1", List.of())))).getRow(1);

    assertThat(row.getCell(6).getCellType()).isEqualTo(CellType.STRING);
    assertThat(row.getCell(6).getStringCellValue()).isEqualTo("=1+1");
  }
}
