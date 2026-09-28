package org.example.conference.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.conference.registration.service.RegistrationQuery.RegistrationView;
import org.springframework.stereotype.Component;

/** Writes accepted registrations to an XLSX workbook; all values are string cells (no formulas). */
@Component
public class ExcelExportWriter {

  static final List<String> HEADERS =
      List.of(
          "Registration ID",
          "Submitted at (UTC)",
          "Participant type",
          "First name",
          "Last name",
          "Email",
          "Organization/institution",
          "Study institution",
          "Study programme",
          "Student ID",
          "Workshops",
          "Events",
          "Meals",
          "Other activities",
          "Consents");

  public byte[] write(List<RegistrationView> registrations) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Registrations");
      CellStyle bold = workbook.createCellStyle();
      Font font = workbook.createFont();
      font.setBold(true);
      bold.setFont(font);
      Row header = sheet.createRow(0);
      for (int i = 0; i < HEADERS.size(); i++) {
        header.createCell(i).setCellValue(HEADERS.get(i));
        header.getCell(i).setCellStyle(bold);
      }
      int rowIndex = 1;
      for (RegistrationView r : registrations) {
        Row row = sheet.createRow(rowIndex++);
        List<String> values =
            List.of(
                r.id().toString(),
                r.submittedAt().toString(),
                r.participantType(),
                r.firstName(),
                r.lastName(),
                r.email(),
                nullToEmpty(r.organization()),
                nullToEmpty(r.studyInstitution()),
                nullToEmpty(r.studyProgramme()),
                nullToEmpty(r.studentId()),
                join(r, "workshops"),
                join(r, "events"),
                join(r, "meals"),
                join(r, "otherActivities"),
                String.join("; ", r.consentIds()));
        for (int i = 0; i < values.size(); i++) {
          row.createCell(i).setCellValue(values.get(i));
        }
      }
      sheet.createFreezePane(0, 1);
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Excel export failed", e);
    }
  }

  private static String join(RegistrationView r, String group) {
    return String.join("; ", r.selectionsByGroup().getOrDefault(group, List.of()));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
