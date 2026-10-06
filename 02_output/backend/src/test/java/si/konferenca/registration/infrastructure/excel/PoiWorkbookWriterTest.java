package si.konferenca.registration.infrastructure.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.application.Fixtures;

class PoiWorkbookWriterTest {

  @Test
  void writesHeaderAndOneStringRowPerRegistration() throws Exception {
    byte[] xlsx = new PoiWorkbookWriter().write(List.of(Fixtures.registration()));

    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      var sheet = wb.getSheet("Registrations");
      Row header = sheet.getRow(0);
      Row row = sheet.getRow(1);
      assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Registration ID");
      assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Type");
      assertThat(header.getCell(10).getStringCellValue()).isEqualTo("Workshops");
      assertThat(row.getCell(2).getStringCellValue()).isEqualTo("EXTERNAL");
      assertThat(row.getCell(10).getStringCellValue()).isEqualTo("AI workshop");
      assertThat(row.getCell(11).getStringCellValue()).isEqualTo("Gala dinner");
      assertThat(row.getCell(9).getStringCellValue()).isEmpty();
      for (int i = 0; i < header.getLastCellNum(); i++) {
        assertThat(row.getCell(i).getCellType()).isEqualTo(CellType.STRING);
      }
      assertThat(header.getLastCellNum()).isEqualTo((short) 16);
      assertThat(sheet.getLastRowNum()).isEqualTo(1);
    }
  }

  @Test
  void writesConsecutiveRowsForSeveralRegistrations() throws Exception {
    byte[] xlsx =
        new PoiWorkbookWriter().write(List.of(Fixtures.registration(), Fixtures.registration()));

    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      var sheet = wb.getSheetAt(0);
      assertThat(sheet.getLastRowNum()).isEqualTo(2);
      assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("Ana");
      assertThat(sheet.getRow(0).getCell(0).getCellStyle().getFontIndex()).isPositive();
    }
  }

  @Test
  void exportContainsNoInternalFields() throws Exception {
    byte[] xlsx = new PoiWorkbookWriter().write(List.of());

    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Row header = wb.getSheetAt(0).getRow(0);
      for (int i = 0; i < header.getLastCellNum(); i++) {
        assertThat(header.getCell(i).getStringCellValue())
            .doesNotContainIgnoringCase("json")
            .doesNotContainIgnoringCase("normalized");
      }
    }
  }
}
