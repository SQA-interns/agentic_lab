package si.konferenca.registration.adapter.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.Field;
import si.konferenca.registration.domain.Fixtures;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.RegistrationValidator;
import si.konferenca.registration.domain.Submission;

class PoiWorkbookWriterTest {

  private static XSSFWorkbook read(byte[] bytes) throws IOException {
    return new XSSFWorkbook(new ByteArrayInputStream(bytes));
  }

  @Test
  void ac00802_emptyExportHasOnlyHeadings() throws IOException {
    try (XSSFWorkbook workbook = read(new PoiWorkbookWriter().write(List.of()))) {
      assertThat(workbook.getSheet("Registrations").getLastRowNum()).isZero();
      assertThat(workbook.getSheet("Registrations").getRow(0).getCell(14).getStringCellValue())
          .isEqualTo("Registered at");
    }
  }

  @Test
  void ac00801_rowHoldsEveryValueAsText() throws IOException {
    Registration registration = Fixtures.registration(RegistrationType.EXTERNAL);

    try (XSSFWorkbook workbook = read(new PoiWorkbookWriter().write(List.of(registration)))) {
      Row row = workbook.getSheet("Registrations").getRow(1);
      assertThat(row.getCell(0).getStringCellValue()).isEqualTo(registration.id().toString());
      assertThat(row.getCell(1).getStringCellValue()).isEqualTo("External participant");
      assertThat(row.getCell(5).getStringCellValue()).isEqualTo("IJS");
      assertThat(row.getCell(6).getStringCellValue()).isEmpty();
      assertThat(row.getCell(9).getStringCellValue()).isEqualTo("Workshop A");
      assertThat(row.getCell(10).getStringCellValue()).isEqualTo("Event A");
      assertThat(row.getCell(12).getStringCellValue()).isEmpty();
      assertThat(row.getCell(13).getStringCellValue()).isEqualTo("privacy");
      assertThat(row.getCell(14).getStringCellValue()).isEqualTo("2026-10-09T10:00:00Z");
    }
  }

  @Test
  void sb05_formulaLikeInputIsStoredAsText() throws IOException {
    Map<Field, String> values = Fixtures.externalValues();
    values.put(Field.ORGANIZATION, "=HYPERLINK(\"http://x\",\"y\")");
    Registration registration =
        new RegistrationValidator(Fixtures.catalogue(), Fixtures.CLOCK)
            .validate(
                new Submission(RegistrationType.EXTERNAL, values, List.of(), List.of("privacy")))
            .registration();

    try (XSSFWorkbook workbook = read(new PoiWorkbookWriter().write(List.of(registration)))) {
      var cell = workbook.getSheet("Registrations").getRow(1).getCell(5);
      assertThat(cell.getCellType()).isEqualTo(CellType.STRING);
      assertThat(cell.getStringCellValue()).isEqualTo("=HYPERLINK(\"http://x\",\"y\")");
    }
  }
}
