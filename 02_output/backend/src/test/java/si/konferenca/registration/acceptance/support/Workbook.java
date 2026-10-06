package si.konferenca.registration.acceptance.support;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Reads the first sheet of an exported workbook as text rows. */
public final class Workbook {

  private final List<List<String>> rows = new ArrayList<>();
  private final List<List<CellType>> types = new ArrayList<>();

  public Workbook(byte[] xlsx) {
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = wb.getSheetAt(0);
      DataFormatter f = new DataFormatter();
      for (Row row : sheet) {
        List<String> values = new ArrayList<>();
        List<CellType> cellTypes = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
          Cell c = row.getCell(i);
          values.add(c == null ? "" : f.formatCellValue(c));
          cellTypes.add(c == null ? CellType.BLANK : c.getCellType());
        }
        rows.add(values);
        types.add(cellTypes);
      }
    } catch (Exception e) {
      throw new IllegalStateException("not a valid xlsx workbook", e);
    }
  }

  public List<String> header() {
    return rows.get(0);
  }

  public List<List<String>> dataRows() {
    return rows.subList(1, rows.size());
  }

  /** The data row containing the value in any cell. */
  public List<String> rowContaining(String value) {
    return dataRows().stream().filter(r -> r.contains(value)).findFirst().orElse(null);
  }

  /** Cell types of the data row containing the value. */
  public List<CellType> typesOfRowContaining(String value) {
    for (int i = 1; i < rows.size(); i++) {
      if (rows.get(i).contains(value)) {
        return types.get(i);
      }
    }
    return null;
  }

  public int rowCount() {
    return rows.size();
  }
}
