package si.konferenca.registration.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import si.konferenca.registration.IntegrationTestBase;
import si.konferenca.registration.RegistrationJson;

class ExportApiIntegrationTest extends IntegrationTestBase {

  private static final String EXPORT = "/api/admin/registrations/export";

  private void register(String json) throws Exception {
    mvc.perform(
            post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(json))
        .andExpect(status().isCreated());
  }

  private Sheet export() throws Exception {
    byte[] bytes =
        mvc.perform(get(EXPORT).with(httpBasic(ORGANIZER_USER, ORGANIZER_PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(
                content()
                    .contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        Matchers.matchesPattern(
                            "attachment; filename=\"registrations-\\d{8}-\\d{6}\\.xlsx\"")))
            .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      return workbook.getSheet("Registrations");
    }
  }

  @Test
  void exportWithoutCredentialsIsRefused() throws Exception {
    register(RegistrationJson.external().build());
    mvc.perform(get(EXPORT))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", Matchers.startsWith("Basic")))
        .andExpect(content().string(Matchers.not(Matchers.containsString("Šušteršič"))));
  }

  @Test
  void exportWithWrongCredentialsIsRefused() throws Exception {
    register(RegistrationJson.external().build());
    mvc.perform(get(EXPORT).with(httpBasic(ORGANIZER_USER, "wrong")))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(Matchers.not(Matchers.containsString("Šušteršič"))));
    mvc.perform(get(EXPORT).with(httpBasic("someone", ORGANIZER_PASSWORD)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void emptyExportHasHeaderRowOnly() throws Exception {
    Sheet sheet = export();
    assertThat(sheet.getLastRowNum()).isZero();
    assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Registration ID");
  }

  @Test
  void exportContainsCurrentRegistrationsWithUnicode() throws Exception {
    register(RegistrationJson.external().build());
    Sheet first = export();
    assertThat(first.getLastRowNum()).isEqualTo(1);
    assertThat(first.getRow(1).getCell(2).getStringCellValue()).isEqualTo("External participant");
    assertThat(first.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Žiga");
    assertThat(first.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Šušteršič");
    assertThat(first.getRow(1).getCell(10).getStringCellValue())
        .isEqualTo("Workshop: Software testing");
    assertThat(first.getRow(1).getCell(12).getStringCellValue()).isEqualTo("Lunch");
    assertThat(first.getRow(1).getCell(14).getStringCellValue()).isEqualTo("privacy");

    register(RegistrationJson.student().build());
    Sheet second = export();
    assertThat(second.getLastRowNum()).isEqualTo(2);
    assertThat(second.getRow(2).getCell(2).getStringCellValue()).isEqualTo("Student");
    assertThat(second.getRow(2).getCell(9).getStringCellValue()).isEqualTo("63210001");
    assertThat(second.getRow(2).getCell(11).getStringCellValue()).isEqualTo("Conference dinner");
  }
}
