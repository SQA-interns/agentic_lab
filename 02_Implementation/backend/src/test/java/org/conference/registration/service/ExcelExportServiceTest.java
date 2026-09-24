package org.conference.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.conference.registration.domain.OptionCategory;
import org.conference.registration.domain.ParticipantDetails;
import org.conference.registration.domain.Registration;
import org.conference.registration.domain.RegistrationType;
import org.conference.registration.domain.SelectedOption;
import org.conference.registration.repository.RegistrationRepository;
import org.junit.jupiter.api.Test;

class ExcelExportServiceTest {

  private final RegistrationRepository repository = mock(RegistrationRepository.class);
  private final ExcelExportService service = new ExcelExportService(repository);

  @Test
  void emptyExportContainsOnlyHeaderRow() throws Exception {
    when(repository.findAllWithOptions()).thenReturn(List.of());
    try (XSSFWorkbook wb = read(service.exportRegistrations())) {
      Sheet sheet = wb.getSheet("Registrations");
      assertThat(sheet.getLastRowNum()).isZero();
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("ID");
      assertThat(sheet.getRow(0).getLastCellNum())
          .isEqualTo((short) ExcelExportService.HEADERS.size());
    }
  }

  @Test
  void writesOneRowPerRegistrationWithOptionsPerCategory() throws Exception {
    Registration student =
        new Registration(
            UUID.randomUUID(),
            RegistrationType.STUDENT,
            ParticipantDetails.student(
                "Špela", "Kovač", "spela@um.si", "UM", "Informatika", "E123"),
            true,
            List.of(
                new SelectedOption("ws-a", OptionCategory.WORKSHOP, "Workshop A"),
                new SelectedOption("ws-b", OptionCategory.WORKSHOP, "Workshop B"),
                new SelectedOption("meal-1", OptionCategory.MEAL, "Lunch")),
            Instant.parse("2026-05-01T10:00:00Z"),
            "f.json");
    when(repository.findAllWithOptions()).thenReturn(List.of(student));

    try (XSSFWorkbook wb = read(service.exportRegistrations())) {
      Row row = wb.getSheet("Registrations").getRow(1);
      assertThat(row.getCell(1).getStringCellValue()).isEqualTo("STUDENT");
      assertThat(row.getCell(3).getStringCellValue()).isEqualTo("Špela");
      assertThat(row.getCell(6).getStringCellValue()).isEmpty();
      assertThat(row.getCell(9).getStringCellValue()).isEqualTo("E123");
      assertThat(row.getCell(11).getStringCellValue()).isEqualTo("Workshop A; Workshop B");
      assertThat(row.getCell(13).getStringCellValue()).isEqualTo("Lunch");
    }
  }

  @Test
  void formulaLikeValuesAreWrittenAsQuotedText() throws Exception {
    Registration external =
        new Registration(
            UUID.randomUUID(),
            RegistrationType.EXTERNAL,
            ParticipantDetails.external("Ana", "Novak", "ana@example.si", "=HYPERLINK(\"x\")"),
            true,
            List.of(),
            Instant.parse("2026-05-01T10:00:00Z"),
            "f.json");
    when(repository.findAllWithOptions()).thenReturn(List.of(external));

    try (XSSFWorkbook wb = read(service.exportRegistrations())) {
      var cell = wb.getSheet("Registrations").getRow(1).getCell(6);
      assertThat(cell.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
      assertThat(cell.getStringCellValue()).isEqualTo("=HYPERLINK(\"x\")");
      assertThat(cell.getCellStyle().getQuotePrefixed()).isTrue();
    }
  }

  @Test
  void detectsFormulaPrefixes() {
    assertThat(ExcelExportService.isFormulaLike("=1+1")).isTrue();
    assertThat(ExcelExportService.isFormulaLike("+1")).isTrue();
    assertThat(ExcelExportService.isFormulaLike("-1")).isTrue();
    assertThat(ExcelExportService.isFormulaLike("@SUM")).isTrue();
    assertThat(ExcelExportService.isFormulaLike("\tx")).isTrue();
    assertThat(ExcelExportService.isFormulaLike("Ana")).isFalse();
    assertThat(ExcelExportService.isFormulaLike("")).isFalse();
  }

  private static XSSFWorkbook read(byte[] bytes) throws Exception {
    return new XSSFWorkbook(new ByteArrayInputStream(bytes));
  }
}
