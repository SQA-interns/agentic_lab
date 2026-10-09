package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Api.array;
import static si.konferenca.registration.acceptance.support.Api.cell;
import static si.konferenca.registration.acceptance.support.Api.external;
import static si.konferenca.registration.acceptance.support.Api.getWithBasic;
import static si.konferenca.registration.acceptance.support.Api.register;
import static si.konferenca.registration.acceptance.support.Api.student;
import static si.konferenca.registration.acceptance.support.Api.uniqueEmail;
import static si.konferenca.registration.acceptance.support.Api.workbookRows;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Api.Response;
import si.konferenca.registration.acceptance.support.Backend;
import tools.jackson.databind.node.ObjectNode;

/** US-008 Registration export as an Excel workbook, organizer only (BR-08). */
class Us008ExportAcceptanceTest {

  static final List<String> HEADINGS =
      List.of(
          "Registration ID",
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
          "Registered at");

  private static Backend backend;
  private static String externalEmail;
  private static String studentEmail;

  @BeforeAll
  static void start() {
    backend = Backend.startDefault();
    externalEmail = uniqueEmail();
    studentEmail = uniqueEmail();
    ObjectNode externalBody = external(externalEmail);
    externalBody.put("firstName", "Špela");
    externalBody.put("organization", "Zavod Čebelica");
    externalBody.set("optionIds", array("ws-testing-ai", "ev-gala-dinner", "meal-lunch-day1"));
    externalBody.set("consentIds", array("data-processing", "photo"));
    assertThat(register(backend, externalBody).status()).isEqualTo(201);
    assertThat(register(backend, student(studentEmail)).status()).isEqualTo(201);
  }

  @AfterAll
  static void stop() {
    backend.close();
  }

  private static String exportUrl(Backend target) {
    return target.url("/api/registrations/export");
  }

  @Test
  void AC_008_01_organizerExportsEveryRegistrationOfBothTypes() {
    Response response = Api.export(backend);

    assertThat(response.header("Content-Disposition")).contains("attachment");
    List<List<String>> rows = workbookRows(response);
    assertThat(rows.getFirst()).containsExactlyElementsOf(HEADINGS);
    assertThat(rows).hasSize(3);
    List<String> externalRow =
        rows.stream().filter(r -> r.contains(externalEmail)).findFirst().orElseThrow();
    List<String> studentRow =
        rows.stream().filter(r -> r.contains(studentEmail)).findFirst().orElseThrow();
    assertThat(cell(rows, externalRow, "Type")).isEqualTo("External participant");
    assertThat(cell(rows, studentRow, "Type")).isEqualTo("Student");
    assertThat(cell(rows, externalRow, "Workshops")).isEqualTo("Delavnica: testiranje sistemov UI");
    assertThat(cell(rows, externalRow, "Events")).isEqualTo("Gala večerja");
    assertThat(cell(rows, externalRow, "Meals")).isEqualTo("Lunch, day 1");
    assertThat(cell(rows, externalRow, "Other activities")).isEmpty();
    assertThat(cell(rows, externalRow, "Consents")).isEqualTo("data-processing; photo");
    assertThat(cell(rows, externalRow, "Study institution")).isEmpty();
    assertThat(cell(rows, studentRow, "Organization / institution")).isEmpty();
    assertThat(cell(rows, studentRow, "Student ID")).isEqualTo("E1234567");
    assertThat(cell(rows, studentRow, "Registered at"))
        .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
  }

  @Test
  void AC_008_02_exportWithoutRegistrationsHasOnlyTheHeadings() {
    try (Backend empty = Backend.startDefault()) {
      List<List<String>> rows = workbookRows(Api.export(empty));

      assertThat(rows).hasSize(1);
      assertThat(rows.getFirst()).containsExactlyElementsOf(HEADINGS);
    }
  }

  @Test
  void AC_008_03_exportWithoutOrganizerAccessIsRefused() {
    Response response = Api.get(exportUrl(backend));

    assertThat(response.status()).isEqualTo(401);
    assertThat(response.header("WWW-Authenticate")).startsWith("Basic");
    assertThat(response.contentType()).doesNotStartWith(Api.EXCEL);
    assertThat(response.text()).doesNotContain(externalEmail).doesNotContain(studentEmail);
  }

  @Test
  void AC_008_04_exportWithWrongOrganizerCredentialsIsRefused() {
    Response wrongPassword =
        getWithBasic(exportUrl(backend), Backend.ORGANIZER_USERNAME, "wrong-password-0000000");
    Response wrongUser = getWithBasic(exportUrl(backend), "someone", Backend.ORGANIZER_PASSWORD);

    for (Response response : List.of(wrongPassword, wrongUser)) {
      assertThat(response.status()).isEqualTo(401);
      assertThat(response.contentType()).doesNotStartWith(Api.EXCEL);
      assertThat(response.text()).doesNotContain(externalEmail).doesNotContain(studentEmail);
    }
  }

  @Test
  void AC_008_05_slovenianCharactersAreUnchangedInTheWorkbook() {
    List<List<String>> rows = workbookRows(Api.export(backend));
    List<String> row =
        rows.stream().filter(r -> r.contains(externalEmail)).findFirst().orElseThrow();

    assertThat(cell(rows, row, "First name")).isEqualTo("Špela");
    assertThat(cell(rows, row, "Organization / institution")).isEqualTo("Zavod Čebelica");
    assertThat(cell(rows, row, "Events")).isEqualTo("Gala večerja");
  }
}
