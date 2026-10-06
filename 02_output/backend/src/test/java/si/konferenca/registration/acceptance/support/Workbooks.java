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
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Opens an exported workbook (`openapi.yaml` exportRegistrations). */
public final class Workbooks {

  public static final List<String> COLUMNS =
      List.of(
          "Type",
          "First name",
          "Last name",
          "Email",
          "Organization / institution",
          "Study institution",
          "Study programme",
          "Student ID",
          "Workshops",
          "Events",
          "Meals",
          "Other activities",
          "Consents",
          "Consent given at",
          "Registered at");

  private Workbooks() {}

  /** The "Registrations" sheet as text rows; row 0 is the heading row. */
  public static List<List<String>> rows(byte[] xlsx) {
    try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = workbook.getSheet("Registrations");
      if (sheet == null) {
        throw new AssertionError("workbook has no sheet named Registrations");
      }
      DataFormatter formatter = new DataFormatter();
      List<List<String>> rows = new ArrayList<>();
      for (Row row : sheet) {
        List<String> values = new ArrayList<>();
        for (int c = 0; c < COLUMNS.size(); c++) {
          Cell cell = row.getCell(c);
          values.add(cell == null ? "" : formatter.formatCellValue(cell));
        }
        rows.add(values);
      }
      return rows;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The cell types of every non-empty cell of the sheet. */
  public static List<CellType> cellTypes(byte[] xlsx) {
    try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      List<CellType> types = new ArrayList<>();
      for (Row row : workbook.getSheet("Registrations")) {
        for (Cell cell : row) {
          types.add(cell.getCellType());
        }
      }
      return types;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
