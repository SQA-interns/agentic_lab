package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.options;
import static si.konferenca.registration.acceptance.support.Registrations.student;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.TestStack;
import si.konferenca.registration.acceptance.support.Workbook;
import tools.jackson.databind.node.ObjectNode;

/** US-008 Registration export, as an Excel workbook (export-workbook.json). */
class RegistrationExportAcceptanceTest extends AcceptanceTest {

  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private static final List<String> HEADER =
      List.of(
          "Registration ID",
          "Registered at",
          "Type",
          "First name",
          "Last name",
          "Email",
          "Organization",
          "Study institution",
          "Study programme",
          "Student ID",
          "Workshops",
          "Events",
          "Meals",
          "Other activities",
          "Consents");

  @Test
  void ac_008_01_organizerExportsOneRowPerRegistrationWithAllData() {
    ObjectNode externalRequest =
        options(external(), "ws-ai", "ev-reception", "ev-industry-dinner", "meal-lunch-1");
    ApiClient.Response first = api.register(externalRequest);
    assertThat(first.status()).as(first.text()).isEqualTo(201);
    ObjectNode studentRequest = options(student(), "other-career-fair");
    ApiClient.Response second = api.register(studentRequest);
    assertThat(second.status()).as(second.text()).isEqualTo(201);

    ApiClient.Response response = api.exportAsOrganizer();

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.header("Content-Type")).startsWith(XLSX);
    assertThat(response.header("Content-Disposition"))
        .contains("attachment")
        .contains("registrations.xlsx");
    Workbook workbook = Workbook.parse(response.body());
    assertThat(workbook.sheetNames()).containsExactly("Registrations");
    assertThat(workbook.header()).isEqualTo(HEADER);
    assertThat(workbook.dataRows()).hasSize(2);

    List<String> externalRow = workbook.dataRows().get(0);
    assertThat(workbook.cell(externalRow, "Registration ID"))
        .isEqualTo(first.json().path("registrationId").asString());
    assertThat(Instant.parse(workbook.cell(externalRow, "Registered at")))
        .isEqualTo(Instant.parse(first.json().path("registeredAt").asString()));
    assertThat(workbook.cell(externalRow, "Type")).isEqualTo("External participant");
    assertThat(workbook.cell(externalRow, "First name")).isEqualTo("Ana");
    assertThat(workbook.cell(externalRow, "Last name")).isEqualTo("Novak");
    assertThat(workbook.cell(externalRow, "Email"))
        .isEqualTo(externalRequest.path("email").asString());
    assertThat(workbook.cell(externalRow, "Organization")).isEqualTo("Institut Jožef Stefan");
    assertThat(workbook.cell(externalRow, "Study institution")).isEmpty();
    assertThat(workbook.cell(externalRow, "Student ID")).isEmpty();
    assertThat(workbook.cell(externalRow, "Workshops")).isEqualTo("Delavnica umetne inteligence");
    assertThat(List.of(workbook.cell(externalRow, "Events").split("; ")))
        .containsExactlyInAnyOrder("Welcome reception", "Industry dinner");
    assertThat(workbook.cell(externalRow, "Meals")).isEqualTo("Lunch, day 1");
    assertThat(workbook.cell(externalRow, "Other activities")).isEmpty();
    Matcher consent =
        Pattern.compile("^data-processing \\((.+)\\)$")
            .matcher(workbook.cell(externalRow, "Consents"));
    assertThat(consent.matches()).as(workbook.cell(externalRow, "Consents")).isTrue();
    assertThat(Instant.parse(consent.group(1)))
        .isEqualTo(Instant.parse(first.json().path("registeredAt").asString()));

    List<String> studentRow = workbook.dataRows().get(1);
    assertThat(workbook.cell(studentRow, "Registration ID"))
        .isEqualTo(second.json().path("registrationId").asString());
    assertThat(workbook.cell(studentRow, "Type")).isEqualTo("Student");
    assertThat(workbook.cell(studentRow, "Organization")).isEmpty();
    assertThat(workbook.cell(studentRow, "Study institution"))
        .isEqualTo("Fakulteta za računalništvo in informatiko");
    assertThat(workbook.cell(studentRow, "Study programme"))
        .isEqualTo("Računalništvo in informatika");
    assertThat(workbook.cell(studentRow, "Student ID")).isEqualTo("63200001");
    assertThat(workbook.cell(studentRow, "Other activities")).isEqualTo("Career fair");
    assertThat(workbook.cell(studentRow, "Workshops")).isEmpty();
  }

  @Test
  void ac_008_02_exportWithoutOrganizerAccessIsRefused() {
    ObjectNode request = external();
    request.put("lastName", "Secretname");
    assertThat(api.register(request).status()).isEqualTo(201);

    ApiClient.Response anonymous = api.export(null, null);
    ApiClient.Response wrongPassword = api.export(TestStack.ORGANIZER_USERNAME, "wrong-password");
    ApiClient.Response wrongUser = api.export("someone", TestStack.ORGANIZER_PASSWORD);

    for (ApiClient.Response response : List.of(anonymous, wrongPassword, wrongUser)) {
      assertThat(response.status()).isEqualTo(401);
      assertThat(response.header("Content-Type")).doesNotStartWith(XLSX);
      assertThat(response.text())
          .doesNotContain("Secretname")
          .doesNotContain(request.path("email").asString());
    }
  }

  @Test
  void ac_008_03_exportWithoutRegistrationsHasOnlyTheHeaderRow() {
    ApiClient.Response response = api.exportAsOrganizer();

    assertThat(response.status()).isEqualTo(200);
    Workbook workbook = Workbook.parse(response.body());
    assertThat(workbook.header()).isEqualTo(HEADER);
    assertThat(workbook.dataRows()).isEmpty();
  }

  @Test
  void ac_008_04_slovenianCharactersAppearUnchangedInTheWorkbook() {
    ObjectNode request = student();
    request.put("firstName", "Špela");
    request.put("lastName", "Žagar Čož");
    request.put("studyProgramme", "Biotehnologija – čšž ČŠŽ");
    assertThat(api.register(request).status()).isEqualTo(201);

    ApiClient.Response response = api.exportAsOrganizer();

    assertThat(response.status()).isEqualTo(200);
    Workbook workbook = Workbook.parse(response.body());
    List<String> row = workbook.dataRows().get(0);
    assertThat(workbook.cell(row, "First name")).isEqualTo("Špela");
    assertThat(workbook.cell(row, "Last name")).isEqualTo("Žagar Čož");
    assertThat(workbook.cell(row, "Study programme")).isEqualTo("Biotehnologija – čšž ČŠŽ");
  }

  @Test
  void ac_008_05_workbookContainsOnlyRegistrationData() {
    ObjectNode request = external();
    String email = "Mixed.Export." + UUID.randomUUID().toString().substring(0, 8) + "@Example.si";
    request.put("email", email);
    request.put("organization", "=HYPERLINK(\"https://evil.example\")");
    assertThat(api.register(request).status()).isEqualTo(201);

    ApiClient.Response response = api.exportAsOrganizer();

    assertThat(response.status()).isEqualTo(200);
    Workbook workbook = Workbook.parse(response.body());
    assertThat(workbook.header()).isEqualTo(HEADER);
    List<String> row = workbook.dataRows().get(0);
    assertThat(row.size()).isLessThanOrEqualTo(HEADER.size());
    assertThat(workbook.cell(row, "Organization"))
        .isEqualTo("=HYPERLINK(\"https://evil.example\")");
    assertThat(workbook.cellTypes()).containsOnly(CellType.STRING);
    assertThat(String.join("|", row))
        .doesNotContain(email.toLowerCase())
        .doesNotContain(TestStack.CAPTCHA_TOKEN);
  }
}
