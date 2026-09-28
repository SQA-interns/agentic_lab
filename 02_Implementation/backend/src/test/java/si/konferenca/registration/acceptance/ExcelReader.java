package si.konferenca.registration.acceptance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Reads the exported workbook (specification §7) as rows of strings; row 0 is the header. */
final class ExcelReader {

  static final int COLUMNS = 15;

  private ExcelReader() {}

  static List<List<String>> rows(byte[] xlsx) {
    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = wb.getSheet("Registrations");
      if (sheet == null) {
        throw new AssertionError("Workbook has no sheet named 'Registrations'");
      }
      List<List<String>> out = new ArrayList<>();
      for (int i = 0; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        List<String> values = new ArrayList<>();
        for (int c = 0; c < COLUMNS; c++) {
          Cell cell = row == null ? null : row.getCell(c);
          values.add(cellText(cell));
        }
        out.add(values);
      }
      return out;
    } catch (IOException e) {
      throw new AssertionError("Response is not a readable .xlsx workbook", e);
    }
  }

  static CellType typeOf(byte[] xlsx, int rowIndex, int column) {
    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      return wb.getSheet("Registrations").getRow(rowIndex).getCell(column).getCellType();
    } catch (IOException e) {
      throw new AssertionError("Response is not a readable .xlsx workbook", e);
    }
  }

  private static String cellText(Cell cell) {
    if (cell == null) {
      return "";
    }
    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue();
      case BLANK -> "";
      case NUMERIC -> String.valueOf(cell.getNumericCellValue());
      case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
      case FORMULA -> "=" + cell.getCellFormula();
      default -> cell.toString();
    };
  }
}
