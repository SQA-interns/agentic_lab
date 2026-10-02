package si.konferenca.registration.acceptance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Reads an exported Excel workbook the way a spreadsheet program shows it. */
final class Workbooks {

  private Workbooks() {}

  /** The rows of the first sheet; each row is the displayed text of its cells. */
  static List<List<String>> rowsOf(byte[] workbookBytes) {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
      DataFormatter formatter = new DataFormatter();
      Sheet sheet = workbook.getSheetAt(0);
      List<List<String>> rows = new ArrayList<>();
      for (Row row : sheet) {
        List<String> cells = new ArrayList<>();
        for (Cell cell : row) {
          cells.add(formatter.formatCellValue(cell));
        }
        rows.add(cells);
      }
      return rows;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static int sheetCount(byte[] workbookBytes) {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
      return workbook.getNumberOfSheets();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
