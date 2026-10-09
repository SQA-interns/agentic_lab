package si.konferenca.registration.acceptance.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** An exported workbook (docs/02_contracts/export-workbook.json) read into plain strings. */
public final class Workbook {

  private final List<String> sheetNames;
  private final List<List<String>> rows;
  private final List<CellType> cellTypes;

  private Workbook(List<String> sheetNames, List<List<String>> rows, List<CellType> cellTypes) {
    this.sheetNames = sheetNames;
    this.rows = rows;
    this.cellTypes = cellTypes;
  }

  public static Workbook parse(byte[] xlsx) {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      List<String> sheetNames = new ArrayList<>();
      for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
        sheetNames.add(workbook.getSheetName(i));
      }
      Sheet sheet = workbook.getSheetAt(0);
      DataFormatter formatter = new DataFormatter();
      List<List<String>> rows = new ArrayList<>();
      List<CellType> cellTypes = new ArrayList<>();
      for (int r = 0; r <= sheet.getLastRowNum(); r++) {
        Row row = sheet.getRow(r);
        List<String> values = new ArrayList<>();
        if (row != null) {
          for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            values.add(cell == null ? "" : formatter.formatCellValue(cell));
            if (cell != null) {
              cellTypes.add(cell.getCellType());
            }
          }
        }
        rows.add(values);
      }
      return new Workbook(sheetNames, rows, cellTypes);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public List<String> sheetNames() {
    return sheetNames;
  }

  /** All rows of the first sheet, header first; trailing empty cells are kept as "". */
  public List<List<String>> rows() {
    return rows;
  }

  public List<String> header() {
    return rows.isEmpty() ? List.of() : rows.get(0);
  }

  public List<List<String>> dataRows() {
    return rows.size() <= 1 ? List.of() : rows.subList(1, rows.size());
  }

  /** The value in a data row under a header. */
  public String cell(List<String> row, String header) {
    int column = header().indexOf(header);
    if (column < 0) {
      throw new IllegalArgumentException("No column " + header);
    }
    return column < row.size() ? row.get(column) : "";
  }

  /** Types of every non-null cell of the first sheet. */
  public List<CellType> cellTypes() {
    return cellTypes;
  }
}
