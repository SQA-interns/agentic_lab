package si.konferenca.registration.application;

import java.util.List;

/** Writes a one-sheet workbook of string cells (port; docs/02_contracts/export-workbook.json). */
public interface WorkbookWriter {

  byte[] write(String sheetName, List<String> header, List<List<String>> rows);
}
