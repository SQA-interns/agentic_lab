package si.konferenca.registration.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.persistence.RegistrationRepository;

/**
 * The organizer export (US-008): one sheet "Registrations", the columns of `openapi.yaml`
 * x-export-columns, one row per registration by registration time. Every cell is a text cell, so a
 * value starting with "=" is never a formula (AC-008-07); no internal field is exported (SR-07).
 */
@Service
public class ExportService {

  static final List<String> COLUMNS =
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

  private final RegistrationRepository repository;

  public ExportService(RegistrationRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public byte[] workbook() {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Registrations");
      write(sheet.createRow(0), COLUMNS);
      int rowIndex = 1;
      for (Registration registration : repository.findAllByOrderByRegisteredAtAsc()) {
        write(sheet.createRow(rowIndex++), row(registration));
      }
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static List<String> row(Registration registration) {
    Participant p = registration.participant();
    List<String> values = new ArrayList<>();
    values.add(registration.type().label());
    values.add(p.firstName());
    values.add(p.lastName());
    values.add(p.email());
    values.add(p.organization());
    values.add(p.studyInstitution());
    values.add(p.studyProgramme());
    values.add(p.studentId());
    for (Category category : Category.values()) {
      values.add(
          registration.options().stream()
              .filter(o -> o.category() == category)
              .map(o -> o.displayName())
              .collect(Collectors.joining("; ")));
    }
    List<GivenConsent> consents = registration.consents();
    values.add(consents.stream().map(GivenConsent::consentId).collect(Collectors.joining("; ")));
    values.add(
        consents.stream()
            .map(c -> RegistrationCopy.timestamp(c.givenAt()))
            .collect(Collectors.joining("; ")));
    values.add(RegistrationCopy.timestamp(registration.registeredAt()));
    return values;
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
