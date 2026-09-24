package org.conference.registration.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.Map;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** US-008 Excel export against the real database. */
class ExportIT extends AbstractIntegrationTest {

  private static final String EXPORT = "/api/admin/registrations/export";

  @Test
  void exportRequiresCredentials() throws Exception { // AC-008-02
    mvc.perform(get(EXPORT))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("WWW-Authenticate"));
    mvc.perform(get(EXPORT).with(httpBasic(ADMIN_USER, "wrong-password-123")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void emptyExportHasHeaderOnly() throws Exception { // AC-008-03
    try (XSSFWorkbook wb = download()) {
      Sheet sheet = wb.getSheet("Registrations");
      assertThat(sheet.getLastRowNum()).isZero();
      assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("First name");
    }
  }

  @Test
  void exportContainsCurrentRegistrations() throws Exception { // AC-008-01, AC-008-04
    postJson("/api/registrations/external", externalPayload());
    try (XSSFWorkbook wb = download()) {
      assertThat(wb.getSheet("Registrations").getLastRowNum()).isEqualTo(1);
    }

    postJson("/api/registrations/student", studentPayload());
    try (XSSFWorkbook wb = download()) {
      Sheet sheet = wb.getSheet("Registrations");
      assertThat(sheet.getLastRowNum()).isEqualTo(2);
      assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("EXTERNAL");
      assertThat(sheet.getRow(1).getCell(11).getStringCellValue())
          .isEqualTo("Workshop: Secure coding in practice");
      assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("STUDENT");
      assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("Špela");
      assertThat(sheet.getRow(2).getCell(9).getStringCellValue()).isEqualTo("E1234567");
    }
  }

  @Test
  void formulaLikeInputIsExportedAsInertText() throws Exception { // AC-008-05
    Map<String, Object> payload = externalPayload();
    payload.put("organization", "=cmd|' /C calc'!A0");
    postJson("/api/registrations/external", payload);
    try (XSSFWorkbook wb = download()) {
      var cell = wb.getSheet("Registrations").getRow(1).getCell(6);
      assertThat(cell.getStringCellValue()).isEqualTo("=cmd|' /C calc'!A0");
      assertThat(cell.getCellStyle().getQuotePrefixed()).isTrue();
    }
  }

  private XSSFWorkbook download() throws Exception {
    byte[] bytes =
        mvc.perform(get(EXPORT).with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        "Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(
                header()
                    .string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    return new XSSFWorkbook(new ByteArrayInputStream(bytes));
  }
}
