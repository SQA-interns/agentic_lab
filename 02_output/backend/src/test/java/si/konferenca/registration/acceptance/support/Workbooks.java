package si.konferenca.registration.acceptance.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Opens an exported Excel workbook and returns its first sheet as text rows. */
public final class Workbooks {

  private Workbooks() {}

  /** A sheet: its name and its rows of cell texts (header first). */
  public record SheetData(String name, List<List<String>> rows) {

    public List<String> header() {
      return rows.get(0);
    }

    public List<List<String>> dataRows() {
      return rows.subList(1, rows.size());
    }

    /** The cell of a data row under the given header text. */
    public String cell(List<String> row, String column) {
      int index = header().indexOf(column);
      if (index < 0) {
        throw new AssertionError("no column '" + column + "' in " + header());
      }
      return index < row.size() ? row.get(index) : "";
    }
  }

  public static SheetData firstSheet(byte[] xlsx) {
    try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = workbook.getSheetAt(0);
      DataFormatter formatter = new DataFormatter();
      List<List<String>> rows = new ArrayList<>();
      for (Row row : sheet) {
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
          Cell cell = row.getCell(i);
          cells.add(cell == null ? "" : formatter.formatCellValue(cell));
        }
        rows.add(cells);
      }
      return new SheetData(sheet.getSheetName(), rows);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** True when any cell of the sheet is a formula. */
  public static boolean hasFormula(byte[] xlsx) {
    try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      for (Row row : workbook.getSheetAt(0)) {
        for (Cell cell : row) {
          if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
            return true;
          }
        }
      }
      return false;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
