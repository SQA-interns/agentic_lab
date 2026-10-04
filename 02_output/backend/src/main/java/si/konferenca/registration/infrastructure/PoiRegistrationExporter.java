package si.konferenca.registration.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import si.konferenca.registration.application.RegistrationExporter;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;

/**
 * Writes the "Registrations" sheet with the contract's headings; every value is a string cell, so
 * nothing is evaluated as a formula (SB-05), and only registration fields appear (SR-07).
 */
public final class PoiRegistrationExporter implements RegistrationExporter {

  static final List<String> HEADINGS =
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

  @Override
  public byte[] export(List<Registration> registrations) {
    try (XSSFWorkbook book = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = book.createSheet("Registrations");
      write(sheet.createRow(0), HEADINGS);
      int index = 1;
      for (Registration registration : registrations) {
        write(sheet.createRow(index++), values(registration));
      }
      book.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("export workbook could not be written", e);
    }
  }

  private static List<String> values(Registration r) {
    Participant p = r.participant();
    String options =
        r.options().stream()
            .map(o -> o.optionName() + " [" + o.optionId() + "]")
            .collect(Collectors.joining("; "));
    return java.util.Arrays.asList(
        r.id().toString(),
        r.type().label(),
        r.acceptedAt().toString(),
        p.firstName(),
        p.lastName(),
        p.email(),
        p.organization(),
        p.studyInstitution(),
        p.studyProgramme(),
        p.studentId(),
        options,
        r.consentText(),
        r.consentGivenAt().toString());
  }

  private static void write(Row row, List<String> values) {
    for (int column = 0; column < values.size(); column++) {
      String value = values.get(column);
      if (value != null && !value.isEmpty()) {
        row.createCell(column).setCellValue(value);
      }
    }
  }
}
