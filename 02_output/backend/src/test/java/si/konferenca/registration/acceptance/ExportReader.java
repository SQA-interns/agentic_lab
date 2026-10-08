package si.konferenca.registration.acceptance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Reads the exported workbook the way an organizer would: first sheet, header row, data rows. */
final class ExportReader {

  private ExportReader() {}

  /** Data rows keyed by the value of the column whose header mentions "email". */
  static Map<String, Map<String, String>> rowsByEmail(byte[] workbook) {
    Map<String, Map<String, String>> rows = new LinkedHashMap<>();
    for (Map<String, String> row : rows(workbook)) {
      row.entrySet().stream()
          .filter(e -> e.getKey().toLowerCase(Locale.ROOT).contains("email"))
          .findFirst()
          .ifPresent(e -> rows.put(e.getValue(), row));
    }
    return rows;
  }

  static List<Map<String, String>> rows(byte[] workbook) {
    try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
      Sheet sheet = book.getSheetAt(0);
      DataFormatter formatter = new DataFormatter();
      Row header = sheet.getRow(sheet.getFirstRowNum());
      List<String> names = new ArrayList<>();
      for (Cell cell : header) {
        names.add(formatter.formatCellValue(cell));
      }
      List<Map<String, String>> rows = new ArrayList<>();
      for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
        Row row = sheet.getRow(r);
        if (row == null) {
          continue;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int c = 0; c < names.size(); c++) {
          Cell cell = row.getCell(c);
          values.put(names.get(c), cell == null ? "" : formatter.formatCellValue(cell));
        }
        rows.add(values);
      }
      return rows;
    } catch (IOException e) {
      throw new IllegalStateException("not a readable workbook", e);
    }
  }

  /** True if no data cell of the workbook holds a formula. */
  static boolean hasNoFormulas(byte[] workbook) {
    try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
      for (Row row : book.getSheetAt(0)) {
        for (Cell cell : row) {
          if (cell.getCellType() == CellType.FORMULA) {
            return false;
          }
        }
      }
      return true;
    } catch (IOException e) {
      throw new IllegalStateException("not a readable workbook", e);
    }
  }
}
