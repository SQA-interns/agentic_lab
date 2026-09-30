package lab.conference.acceptance.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Parses the organizer export per docs/02_contracts/export-workbook.json. */
public final class Workbook {

  private final List<String> headers = new ArrayList<>();
  private final List<Map<String, Cell>> rows = new ArrayList<>();
  private final String sheetName;

  public Workbook(byte[] xlsx) {
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = wb.getSheetAt(0);
      sheetName = sheet.getSheetName();
      Row header = sheet.getRow(0);
      for (Cell c : header) {
        headers.add(c.getStringCellValue());
      }
      for (int r = 1; r <= sheet.getLastRowNum(); r++) {
        Row row = sheet.getRow(r);
        if (row == null) {
          continue;
        }
        Map<String, Cell> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
          map.put(headers.get(i), row.getCell(i));
        }
        rows.add(map);
      }
    } catch (IOException e) {
      throw new AssertionError("export is not a readable xlsx workbook", e);
    }
  }

  public String sheetName() {
    return sheetName;
  }

  public List<String> headers() {
    return headers;
  }

  public List<Map<String, Cell>> rows() {
    return rows;
  }

  /** The row whose "Registration ID" equals the id, or null. */
  public Map<String, Cell> row(String registrationId) {
    for (Map<String, Cell> row : rows) {
      if (registrationId.equals(text(row.get("Registration ID")))) {
        return row;
      }
    }
    return null;
  }

  public boolean containsText(String value) {
    for (Map<String, Cell> row : rows) {
      for (Cell c : row.values()) {
        if (value.equals(text(c))) {
          return true;
        }
      }
    }
    return false;
  }

  public static String text(Cell cell) {
    return cell == null ? "" : cell.getStringCellValue();
  }
}
