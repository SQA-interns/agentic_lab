package si.konferenca.registration.application;

import java.util.List;
import si.konferenca.registration.domain.Registration;

/** Port: renders registrations as an Excel workbook (specification section 9). */
public interface WorkbookWriter {

  byte[] write(List<Registration> registrations);
}
