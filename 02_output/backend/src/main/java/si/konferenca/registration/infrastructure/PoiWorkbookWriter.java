package si.konferenca.registration.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import si.konferenca.registration.application.WorkbookWriter;

/**
 * Writes .xlsx with Apache POI. Every cell is a string cell, so input such as {@code =HYPERLINK(…)}
 * is never evaluated as a formula (SB-05).
 */
public class PoiWorkbookWriter implements WorkbookWriter {

  private static final int COLUMN_WIDTH = 24 * 256;

  @Override
  public byte[] write(String sheetName, List<String> header, List<List<String>> rows) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XSSFSheet sheet = workbook.createSheet(sheetName);
      CellStyle headerStyle = workbook.createCellStyle();
      Font bold = workbook.createFont();
      bold.setBold(true);
      headerStyle.setFont(bold);
      XSSFRow headerRow = sheet.createRow(0);
      for (int column = 0; column < header.size(); column++) {
        headerRow.createCell(column).setCellValue(header.get(column));
        headerRow.getCell(column).setCellStyle(headerStyle);
        sheet.setColumnWidth(column, COLUMN_WIDTH);
      }
      for (int r = 0; r < rows.size(); r++) {
        XSSFRow row = sheet.createRow(r + 1);
        List<String> values = rows.get(r);
        for (int column = 0; column < values.size(); column++) {
          row.createCell(column).setCellValue(values.get(column));
        }
      }
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Workbook could not be written", e);
    }
  }
}
