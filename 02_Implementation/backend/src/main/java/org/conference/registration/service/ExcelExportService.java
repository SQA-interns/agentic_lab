package org.conference.registration.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.conference.registration.domain.OptionCategory;
import org.conference.registration.domain.ParticipantDetails;
import org.conference.registration.domain.Registration;
import org.conference.registration.domain.SelectedOption;
import org.conference.registration.repository.RegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Excel export of all current registrations (US-008). */
@Service
public class ExcelExportService {

  static final List<String> HEADERS =
      List.of(
          "ID",
          "Type",
          "Created at (UTC)",
          "First name",
          "Last name",
          "Email",
          "Organization / institution",
          "Study institution",
          "Study programme",
          "Student ID",
          "Privacy consent",
          "Workshops",
          "Events",
          "Meals",
          "Other activities");

  private final RegistrationRepository repository;

  public ExcelExportService(RegistrationRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public byte[] exportRegistrations() {
    List<Registration> registrations = repository.findAllWithOptions();
    try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Styles styles = new Styles(workbook);
      Sheet sheet = workbook.createSheet("Registrations");
      Row header = sheet.createRow(0);
      for (int i = 0; i < HEADERS.size(); i++) {
        Cell cell = header.createCell(i);
        cell.setCellValue(HEADERS.get(i));
        cell.setCellStyle(styles.header());
      }
      int rowIndex = 1;
      for (Registration registration : registrations) {
        writeRow(sheet.createRow(rowIndex++), registration, styles);
      }
      sheet.createFreezePane(0, 1);
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Excel export failed", e);
    }
  }

  private static void writeRow(Row row, Registration r, Styles styles) {
    ParticipantDetails p = r.getParticipant();
    List<String> values =
        List.of(
            r.getId().toString(),
            r.getType().name(),
            r.getCreatedAt().toString(),
            p.firstName(),
            p.lastName(),
            p.email(),
            nullToEmpty(p.organization()),
            nullToEmpty(p.studyInstitution()),
            nullToEmpty(p.studyProgramme()),
            nullToEmpty(p.studentId()),
            r.isPrivacyConsent() ? "yes" : "no",
            optionNames(r, OptionCategory.WORKSHOP),
            optionNames(r, OptionCategory.EVENT),
            optionNames(r, OptionCategory.MEAL),
            optionNames(r, OptionCategory.ACTIVITY));
    for (int i = 0; i < values.size(); i++) {
      String value = values.get(i);
      Cell cell = row.createCell(i);
      // String cells are never evaluated; the quote prefix additionally keeps Excel from
      // re-interpreting formula-like text when the cell is edited (CSV/formula injection).
      cell.setCellValue(value);
      if (isFormulaLike(value)) {
        cell.setCellStyle(styles.quoted());
      }
    }
  }

  static boolean isFormulaLike(String value) {
    if (value.isEmpty()) {
      return false;
    }
    char first = value.charAt(0);
    return first == '='
        || first == '+'
        || first == '-'
        || first == '@'
        || first == '\t'
        || first == '\r';
  }

  private static String optionNames(Registration r, OptionCategory category) {
    return r.getOptions().stream()
        .filter(o -> o.getCategory() == category)
        .map(SelectedOption::getDisplayName)
        .collect(Collectors.joining("; "));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private record Styles(CellStyle header, CellStyle quoted) {
    Styles(SXSSFWorkbook workbook) {
      this(workbook.createCellStyle(), workbook.createCellStyle());
      Font bold = workbook.createFont();
      bold.setBold(true);
      header.setFont(bold);
      quoted.setQuotePrefixed(true);
    }
  }
}
