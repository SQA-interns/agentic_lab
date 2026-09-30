package si.konferenca.registration.application;

import java.util.List;

/** Port: renders registrations as an .xlsx workbook (02_specification.md 5.4). */
public interface WorkbookWriter {

  byte[] write(List<RegistrationCopy> registrations);
}
