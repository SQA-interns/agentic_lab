package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.student;
import static si.konferenca.registration.acceptance.support.TestEnvironment.ORGANIZER_PASSWORD;
import static si.konferenca.registration.acceptance.support.TestEnvironment.ORGANIZER_USERNAME;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Workbooks;
import tools.jackson.databind.node.ObjectNode;

/** US-008 Registration export (BR-08), workbook layout of docs/02_specification.md §7. */
class Us008ExportAcceptanceTest extends AcceptanceTestBase {

  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private static final List<String> COLUMNS =
      List.of(
          "Registration ID",
          "Received at",
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
          "Consents");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    configure(registry, Map.of());
  }

  private String register(ObjectNode request) {
    Api.Response response = api.register(request);
    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    return response.json().path("registrationId").asString();
  }

  private Workbooks.SheetData exportAsOrganizer() {
    Api.Response response = api.export(ORGANIZER_USERNAME, ORGANIZER_PASSWORD);
    assertThat(response.status()).as("export status").isEqualTo(200);
    assertThat(response.contentType()).startsWith(XLSX);
    assertThat(response.header("Content-Disposition"))
        .contains("attachment")
        .contains("registrations.xlsx");
    assertThat(Workbooks.hasFormula(response.body())).as("formula cells").isFalse();
    return Workbooks.firstSheet(response.body());
  }

  private static List<String> rowOf(Workbooks.SheetData sheet, String registrationId) {
    return sheet.dataRows().stream()
        .filter(row -> sheet.cell(row, "Registration ID").equals(registrationId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no row for " + registrationId));
  }

  @Test
  void ac008_01_organizerExportsEveryRegistrationWithAllFieldsOptionsAndConsents() {
    String externalId = register(external());
    String studentId = register(student());

    Workbooks.SheetData sheet = exportAsOrganizer();

    assertThat(sheet.name()).isEqualTo("Registrations");
    assertThat(sheet.header()).containsExactlyElementsOf(COLUMNS);
    assertThat(sheet.dataRows()).hasSize(2);
    List<String> ext = rowOf(sheet, externalId);
    assertThat(sheet.cell(ext, "Type").toLowerCase(Locale.ROOT)).startsWith("external");
    assertThat(sheet.cell(ext, "First name")).isEqualTo("Ana");
    assertThat(sheet.cell(ext, "Last name")).isEqualTo("Novak");
    assertThat(sheet.cell(ext, "Email")).isEqualTo("ana.novak@example.com");
    assertThat(sheet.cell(ext, "Organization / institution")).isEqualTo("Institut Jozef Stefan");
    assertThat(sheet.cell(ext, "Student ID")).isEmpty();
    assertThat(sheet.cell(ext, "Workshops")).contains("Delavnica: testiranje");
    assertThat(sheet.cell(ext, "Meals")).contains("Kosilo");
    assertThat(sheet.cell(ext, "Consents")).contains("data-processing");
    assertThat(sheet.cell(ext, "Received at")).isNotBlank();
    List<String> stu = rowOf(sheet, studentId);
    assertThat(sheet.cell(stu, "Type").toLowerCase(Locale.ROOT)).startsWith("student");
    assertThat(sheet.cell(stu, "Study institution")).isEqualTo("Univerza v Mariboru");
    assertThat(sheet.cell(stu, "Study programme")).isEqualTo("Informatika");
    assertThat(sheet.cell(stu, "Student ID")).isEqualTo("93120045");
    assertThat(sheet.cell(stu, "Organization / institution")).isEmpty();
    assertThat(sheet.cell(stu, "Other activities")).contains("City tour");
  }

  @Test
  void ac008_02_exportWithoutOrganizerAccessIsRefused() {
    register(external());

    Api.Response response = api.export(null, null);

    assertThat(response.status()).isEqualTo(401);
    assertThat(response.contentType()).doesNotStartWith(XLSX);
    assertThat(response.text()).doesNotContain("ana.novak@example.com").doesNotContain("Novak");
  }

  @Test
  void ac008_03_exportWithWrongOrganizerCredentialsIsRefused() {
    register(external());

    Api.Response wrongPassword = api.export(ORGANIZER_USERNAME, ORGANIZER_PASSWORD + "x");
    Api.Response wrongUser = api.export("participant", ORGANIZER_PASSWORD);

    for (Api.Response response : List.of(wrongPassword, wrongUser)) {
      assertThat(response.status()).isEqualTo(401);
      assertThat(response.contentType()).doesNotStartWith(XLSX);
      assertThat(response.text()).doesNotContain("ana.novak@example.com");
    }
  }

  @Test
  void ac008_04_exportWithoutRegistrationsHasOnlyTheHeaderRow() {
    Workbooks.SheetData sheet = exportAsOrganizer();

    assertThat(sheet.header()).containsExactlyElementsOf(COLUMNS);
    assertThat(sheet.dataRows()).isEmpty();
  }

  @Test
  void ac008_05_slovenianCharactersAreUnchangedInTheExport() {
    ObjectNode request = student();
    request.put("firstName", "Žiga");
    request.put("lastName", "Čebašek");
    request.put("studyProgramme", "Računalništvo in informatika");
    String id = register(request);

    Workbooks.SheetData sheet = exportAsOrganizer();

    List<String> row = rowOf(sheet, id);
    assertThat(sheet.cell(row, "First name")).isEqualTo("Žiga");
    assertThat(sheet.cell(row, "Last name")).isEqualTo("Čebašek");
    assertThat(sheet.cell(row, "Study programme")).isEqualTo("Računalništvo in informatika");
  }
}
