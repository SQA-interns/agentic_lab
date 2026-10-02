package si.konferenca.registration.domain;

import java.util.List;

/** Port: renders registrations as an Excel workbook (US-008). */
public interface RegistrationExporter {

  /** The bytes of a workbook with a heading row and one row per registration. */
  byte[] export(List<Registration> registrations);
}
