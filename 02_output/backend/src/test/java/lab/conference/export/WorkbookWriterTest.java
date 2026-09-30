package lab.conference.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lab.conference.registration.AcceptedRegistration;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkbookWriterTest {

  private static AcceptedRegistration reg(String first, Boolean consent) {
    Map<String, String> p = new HashMap<>();
    p.put("firstName", first);
    p.put("lastName", "L");
    p.put("email", "e@example.test");
    p.put("organization", "Org");
    return new AcceptedRegistration(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "external",
        Instant.parse("2026-01-01T00:00:00Z"),
        p,
        Map.of(
            "workshops",
            List.of("A", "B"),
            "events",
            List.of(),
            "meals",
            List.of(),
            "other",
            List.of()),
        consent);
  }

  @Test
  void writesHeaderAndOneStringRowPerRegistration() throws Exception {
    byte[] bytes =
        WorkbookWriter.write(List.of(reg("=1+1", true), reg("Ana", false), reg("Bo", null)));
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = wb.getSheet("Registrations");
      assertThat(sheet.getLastRowNum()).isEqualTo(3);
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Registration ID");
      assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) WorkbookWriter.COLUMNS.size());
      Row first = sheet.getRow(1);
      assertThat(first.getCell(3).getCellType()).isEqualTo(CellType.STRING);
      assertThat(first.getCell(3).getStringCellValue()).isEqualTo("=1+1");
      assertThat(first.getCell(3).getCellStyle().getQuotePrefixed()).isTrue();
      assertThat(first.getCell(4).getCellStyle().getQuotePrefixed()).isFalse();
      assertThat(first.getCell(7).getStringCellValue()).isEmpty();
      assertThat(first.getCell(10).getStringCellValue()).isEqualTo("A; B");
      assertThat(first.getCell(14).getStringCellValue()).isEqualTo("yes");
      assertThat(sheet.getRow(2).getCell(14).getStringCellValue()).isEqualTo("no");
      assertThat(sheet.getRow(3).getCell(14).getStringCellValue()).isEmpty();
      assertThat(first.getCell(2).getStringCellValue()).isEqualTo("2026-01-01T00:00:00Z");
    }
  }

  @Test
  void emptyExportHasOnlyHeader() throws Exception {
    try (XSSFWorkbook wb =
        new XSSFWorkbook(new ByteArrayInputStream(WorkbookWriter.write(List.of())))) {
      assertThat(wb.getSheet("Registrations").getLastRowNum()).isZero();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"=A1", "+1", "-1", "@SUM(1)", "\tx", "\rx"})
  void formulaTriggersNeedQuotePrefix(String value) {
    assertThat(WorkbookWriter.needsQuotePrefix(value)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Ana", "1+1", " =x", "a=b"})
  void ordinaryTextDoesNotNeedQuotePrefix(String value) {
    assertThat(WorkbookWriter.needsQuotePrefix(value)).isFalse();
    assertThat(WorkbookWriter.needsQuotePrefix(null)).isFalse();
  }
}
