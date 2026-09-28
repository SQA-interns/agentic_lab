package org.example.conference.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.conference.support.IntegrationTestBase;
import org.example.conference.support.Payloads;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** US-008, P-09, AC-008-01/02. */
class ExportIT extends IntegrationTestBase {

  private static final String URL = "/api/organizer/registrations/export.xlsx";

  private void registerBoth() throws Exception {
    postJson("/api/registrations/external", Payloads.external()).andExpect(status().isCreated());
    Map<String, Object> student = Payloads.student();
    student.put("firstName", "=HYPERLINK(\"http://evil.example\")");
    postJson("/api/registrations/student", student).andExpect(status().isCreated());
  }

  @Test
  void unauthorizedRequestsRevealNoData() throws Exception {
    registerBoth();
    for (MvcResult result :
        List.of(
            mockMvc.perform(get(URL)).andExpect(status().isUnauthorized()).andReturn(),
            mockMvc
                .perform(get(URL).with(httpBasic("organizer", "wrong-password-xyz")))
                .andExpect(status().isUnauthorized())
                .andReturn(),
            mockMvc
                .perform(get(URL).with(httpBasic("someone", "test-organizer-password")))
                .andExpect(status().isUnauthorized())
                .andReturn())) {
      String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
      assertThat(body).doesNotContain("Špela").doesNotContain("Luka").doesNotContain("@example");
      assertThat(body).doesNotStartWith("PK");
    }
  }

  @Test
  void authorizedExportMatchesAcceptedRecordsFromBothForms() throws Exception {
    registerBoth();
    MvcResult result =
        mockMvc
            .perform(get(URL).with(httpBasic("organizer", "test-organizer-password")))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        "Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn();

    List<List<String>> rows = new ArrayList<>();
    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
      Sheet sheet = workbook.getSheet("Registrations");
      DataFormatter formatter = new DataFormatter();
      for (Row row : sheet) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < ExcelExportWriter.HEADERS.size(); i++) {
          values.add(formatter.formatCellValue(row.getCell(i)));
        }
        rows.add(values);
        for (var cell : row) {
          assertThat(cell.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
        }
      }
    }
    assertThat(rows.get(0)).isEqualTo(ExcelExportWriter.HEADERS);
    List<Map<String, Object>> db =
        jdbc.queryForList(
            "SELECT id::text AS id, participant_type, first_name, last_name, email,"
                + " coalesce(organization,'') AS org, coalesce(student_id,'') AS sid"
                + " FROM registration ORDER BY created_at, id");
    assertThat(rows).hasSize(db.size() + 1);
    for (int i = 0; i < db.size(); i++) {
      List<String> row = rows.get(i + 1);
      Map<String, Object> expected = db.get(i);
      assertThat(row.get(0)).isEqualTo(expected.get("id"));
      assertThat(row.get(2)).isEqualTo(expected.get("participant_type"));
      assertThat(row.get(3)).isEqualTo(expected.get("first_name"));
      assertThat(row.get(4)).isEqualTo(expected.get("last_name"));
      assertThat(row.get(5)).isEqualTo(expected.get("email"));
      assertThat(row.get(6)).isEqualTo(expected.get("org"));
      assertThat(row.get(9)).isEqualTo(expected.get("sid"));
    }
    assertThat(rows.stream().map(r -> r.get(2)).toList()).contains("EXTERNAL", "STUDENT");
    assertThat(rows)
        .anySatisfy(r -> assertThat(r.get(10)).isEqualTo("Workshop: Data science in practice"));
    assertThat(rows).anySatisfy(r -> assertThat(r.get(14)).isEqualTo(Payloads.CONSENT_ID));
  }
}
