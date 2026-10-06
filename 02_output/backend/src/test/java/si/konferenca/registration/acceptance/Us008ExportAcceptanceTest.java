package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.student;

import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Workbooks;

/** US-008 Registration export (`openapi.yaml` exportRegistrations, BR-08). */
class Us008ExportAcceptanceTest extends AcceptanceTestBase {

  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private Api.Response exportAsOrganizer() {
    return api.export(
        AcceptanceEnvironment.ORGANIZER_USERNAME, AcceptanceEnvironment.ORGANIZER_PASSWORD);
  }

  private static List<List<String>> assertWorkbook(Api.Response response) {
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    assertThat(response.header("Content-Type")).startsWith(XLSX);
    assertThat(response.header("Content-Disposition")).contains("attachment");
    List<List<String>> rows = Workbooks.rows(response.body());
    assertThat(rows.get(0)).containsExactlyElementsOf(Workbooks.COLUMNS);
    return rows;
  }

  private static void assertRefused(Api.Response response) {
    assertThat(response.status()).as(response.toString()).isEqualTo(401);
    assertThat(response.json().path("code").asString()).isEqualTo("UNAUTHORIZED");
    assertThat(response.header("Content-Type")).doesNotStartWith(XLSX);
    assertThat(response.header("WWW-Authenticate")).startsWith("Basic");
  }

  @Test
  void AC_008_01_organizer_receives_a_workbook_with_one_row_per_registration() {
    Map<String, Object> ext = external();
    ext.put(
        "optionIds", List.of("ws-ai-research", "ws-open-data", "ev-welcome", "meal-lunch-day1"));
    Map<String, Object> stu = student();
    assertAccepted(api.register(ext));
    assertAccepted(api.register(stu));

    List<List<String>> rows = assertWorkbook(exportAsOrganizer());

    assertThat(rows).hasSize(3);
    List<String> first = rows.get(1);
    assertThat(first.subList(0, 8))
        .containsExactly(
            "External participant",
            "Janez",
            "Novak",
            (String) ext.get("email"),
            "Institute of Testing",
            "",
            "",
            "");
    assertThat(first.get(8)).isEqualTo("Workshop: AI in research; Workshop: Open data");
    assertThat(first.get(9)).isEqualTo("Welcome reception");
    assertThat(first.get(10)).isEqualTo("Lunch, day 1");
    assertThat(first.get(11)).isEmpty();
    assertThat(first.get(12)).isNotBlank();
    assertThat(first.get(13)).isNotBlank();
    assertThat(first.get(14)).isNotBlank();
    List<String> second = rows.get(2);
    assertThat(second.subList(0, 8))
        .containsExactly(
            "Student",
            "Ana",
            "Horvat",
            (String) stu.get("email"),
            "",
            "University of Ljubljana",
            "Computer Science",
            "63210001");
    assertThat(second.get(8)).isEqualTo("Workshop: Open data");
    assertThat(second.get(10)).isEqualTo("Lunch, day 2");
  }

  @Test
  void AC_008_02_export_without_organizer_access_is_refused() {
    assertAccepted(api.register(external()));

    assertRefused(api.exportWithoutCredentials());
  }

  @Test
  void AC_008_03_export_with_wrong_credentials_is_refused() {
    assertAccepted(api.register(external()));

    assertRefused(api.export(AcceptanceEnvironment.ORGANIZER_USERNAME, "wrong-password-123456"));
    assertRefused(api.export("someone-else", AcceptanceEnvironment.ORGANIZER_PASSWORD));
  }

  @Test
  void AC_008_04_workbook_contains_only_registration_data() {
    String id = assertAccepted(api.register(external())).path("id").asString();
    String copyName = copies.copies().get(0).getFileName().toString();

    List<List<String>> rows = assertWorkbook(exportAsOrganizer());

    assertThat(rows.get(0)).hasSize(Workbooks.COLUMNS.size());
    for (List<String> row : rows) {
      for (String cell : row) {
        assertThat(cell).doesNotContain(id).doesNotContain(copyName);
      }
    }
  }

  @Test
  void AC_008_05_export_without_registrations_has_headings_and_no_rows() {
    List<List<String>> rows = assertWorkbook(exportAsOrganizer());

    assertThat(rows).hasSize(1);
  }

  @Test
  void AC_008_06_slovenian_characters_are_unchanged_in_the_export() {
    Map<String, Object> body = student();
    body.put("firstName", "Špela");
    body.put("lastName", "Žagar");
    body.put("studyInstitution", "Univerza v Ljubljani, Fakulteta za računalništvo");
    assertAccepted(api.register(body));

    List<String> row = assertWorkbook(exportAsOrganizer()).get(1);

    assertThat(row.get(1)).isEqualTo("Špela");
    assertThat(row.get(2)).isEqualTo("Žagar");
    assertThat(row.get(5)).isEqualTo("Univerza v Ljubljani, Fakulteta za računalništvo");
  }

  @Test
  void AC_008_07_values_starting_with_a_formula_character_are_exported_as_text() {
    Map<String, Object> body = external();
    body.put("lastName", "=1+2");
    body.put("organization", "=HYPERLINK(\"http://example.com\",\"x\")");
    assertAccepted(api.register(body));

    Api.Response response = exportAsOrganizer();
    List<String> row = assertWorkbook(response).get(1);

    assertThat(row.get(2)).isEqualTo("=1+2");
    assertThat(row.get(4)).isEqualTo("=HYPERLINK(\"http://example.com\",\"x\")");
    assertThat(Workbooks.cellTypes(response.body())).doesNotContain(CellType.FORMULA);
  }
}
