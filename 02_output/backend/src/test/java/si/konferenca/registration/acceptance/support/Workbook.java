package si.konferenca.registration.acceptance.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Parsed export workbook: the "Registrations" sheet as rows of cell texts. */
public record Workbook(List<String> headings, List<List<String>> rows, boolean allStringCells) {

  public static final List<String> HEADINGS =
      List.of(
          "Registration ID",
          "Type",
          "Accepted at (UTC)",
          "First name",
          "Last name",
          "Email",
          "Organization / institution",
          "Study institution",
          "Study programme",
          "Student ID",
          "Options",
          "Consent",
          "Consent given at (UTC)");

  public static Workbook parse(byte[] xlsx) {
    try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = book.getSheet("Registrations");
      if (sheet == null) {
        throw new IllegalStateException("no sheet named Registrations");
      }
      List<List<String>> all = new ArrayList<>();
      boolean strings = true;
      for (Row row : sheet) {
        List<String> cells = new ArrayList<>();
        for (int c = 0; c < HEADINGS.size(); c++) {
          Cell cell = row.getCell(c);
          if (cell == null || cell.getCellType() == CellType.BLANK) {
            cells.add("");
          } else {
            strings &= cell.getCellType() == CellType.STRING;
            cells.add(cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : "?");
          }
        }
        all.add(cells);
      }
      List<String> headings = all.isEmpty() ? List.of() : all.get(0);
      return new Workbook(
          headings, all.isEmpty() ? List.of() : all.subList(1, all.size()), strings);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The row whose Email column equals the address, if any. */
  public Optional<List<String>> rowFor(String email) {
    int column = HEADINGS.indexOf("Email");
    return rows.stream().filter(r -> r.get(column).equals(email)).findFirst();
  }

  public static String cell(List<String> row, String heading) {
    return row.get(HEADINGS.indexOf(heading));
  }
}
