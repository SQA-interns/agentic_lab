package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class PoiWorkbookWriterTest {

  @Test
  void writesHeaderAndRowsAsStringCells() throws Exception {
    byte[] xlsx =
        new PoiWorkbookWriter()
            .write(
                "Registrations",
                List.of("A", "B"),
                List.of(List.of("=1+1", ""), List.of("Čšž", "@x")));

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = workbook.getSheet("Registrations");
      assertThat(sheet.getLastRowNum()).isEqualTo(2);
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("A");
      assertThat(sheet.getRow(1).getCell(0).getCellType()).isEqualTo(CellType.STRING);
      assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("=1+1");
      assertThat(sheet.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.STRING);
      assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Čšž");
      assertThat(sheet.getRow(0).getCell(0).getCellStyle().getFontIndex()).isPositive();
    }
  }

  @Test
  void emptyRowsGiveOnlyTheHeader() throws Exception {
    byte[] xlsx = new PoiWorkbookWriter().write("S", List.of("A"), List.of());

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      assertThat(workbook.getSheet("S").getLastRowNum()).isZero();
    }
  }
}
