package si.konferenca.registration.infrastructure.export;

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
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

class PoiExportWriterTest {

  private static final Instant AT = Instant.parse("2026-10-06T08:00:00.123Z");

  private static Registration registration(String firstName) {
    return new Registration(
        UUID.fromString("3f1c2a9e-5b7d-4c1e-9a2b-8d6e4f0a1b2c"),
        RegistrationType.EXTERNAL,
        new Participant(firstName, "Novak", "a@b.si", "IJS", null, null, null),
        List.of(
            new Registration.SelectedOption("w1", "WS 1", OptionCategory.WORKSHOP),
            new Registration.SelectedOption("w2", "WS 2", OptionCategory.WORKSHOP),
            new Registration.SelectedOption("t", "Tour", OptionCategory.OTHER)),
        List.of(
            new Registration.GivenConsent("data", "I agree", AT),
            new Registration.GivenConsent("photo", "Photos", AT)),
        AT);
  }

  @Test
  void cellsFollowTheColumnOrderOfTheSpecification() {
    assertThat(PoiExportWriter.cells(registration("Ana")))
        .containsExactly(
            "3f1c2a9e-5b7d-4c1e-9a2b-8d6e4f0a1b2c",
            "2026-10-06T08:00:00.123Z",
            "external",
            "Ana",
            "Novak",
            "a@b.si",
            "IJS",
            "",
            "",
            "",
            "WS 1; WS 2",
            "",
            "",
            "Tour",
            "data (2026-10-06T08:00:00.123Z); photo (2026-10-06T08:00:00.123Z)");
    assertThat(PoiExportWriter.COLUMNS).hasSize(PoiExportWriter.cells(registration("Ana")).size());
  }

  @Test
  void formulaLikeInputStaysATextCell() throws IOException {
    byte[] xlsx = new PoiExportWriter().write(List.of(registration("=HYPERLINK(\"http://x\")")));

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = workbook.getSheet("Registrations");
      assertThat(sheet.getLastRowNum()).isEqualTo(1);
      for (Row row : sheet) {
        row.forEach(cell -> assertThat(cell.getCellType()).isEqualTo(CellType.STRING));
      }
      assertThat(sheet.getRow(1).getCell(3).getStringCellValue())
          .isEqualTo("=HYPERLINK(\"http://x\")");
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Registration ID");
      assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
    }
  }

  @Test
  void everyRegistrationGetsItsOwnRowInOrder() throws IOException {
    byte[] xlsx =
        new PoiExportWriter().write(List.of(registration("First"), registration("Second")));

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = workbook.getSheetAt(0);
      assertThat(sheet.getLastRowNum()).isEqualTo(2);
      assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("First");
      assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("Second");
    }
  }

  @Test
  void emptyExportHasOnlyTheHeader() throws IOException {
    byte[] xlsx = new PoiExportWriter().write(List.of());

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = workbook.getSheetAt(0);
      assertThat(sheet.getLastRowNum()).isZero();
      assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 15);
    }
  }
}
