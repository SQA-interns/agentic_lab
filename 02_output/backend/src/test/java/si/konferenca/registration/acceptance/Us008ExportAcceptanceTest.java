package si.konferenca.registration.acceptance;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.Registrations;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.Workbook;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-008 Registration export. */
class Us008ExportAcceptanceTest extends AcceptanceTestBase {

  private static final String CONSENT_TEXT =
      "I agree that the organizers process my personal data given in this form to organise my"
          + " attendance at the conference and the activities I selected.";

  private Workbook export() {
    ApiClient.Response response = api.export(api.organizerToken());
    assertThat(response.status()).as(response.text()).isEqualTo(200);
    return Workbook.parse(response.body());
  }

  @Test
  @DisplayName("AC-008-01 the organizer exports one row per registration with all fields")
  void ac008_01_organizerExportsAllRegistrations() {
    ObjectNode external = Registrations.external();
    JsonNode externalAccepted = registerAccepted(external);
    ObjectNode student = Registrations.withOptions(Registrations.student());
    JsonNode studentAccepted = registerAccepted(student);

    ApiClient.Response response = api.export(api.organizerToken());

    assertThat(response.status()).as(response.text()).isEqualTo(200);
    assertThat(response.contentType()).startsWith(ApiClient.XLSX);
    assertThat(response.header("Content-Disposition"))
        .contains("attachment")
        .contains("registrations.xlsx");
    Workbook workbook = Workbook.parse(response.body());
    assertThat(workbook.headings()).isEqualTo(Workbook.HEADINGS);
    assertThat(workbook.allStringCells()).isTrue();

    List<String> e = workbook.rowFor(Registrations.email(external)).orElseThrow();
    assertThat(Workbook.cell(e, "Registration ID")).isEqualTo(idOf(externalAccepted).toString());
    assertThat(Workbook.cell(e, "Type")).isEqualTo("External participant");
    assertThat(Instant.parse(Workbook.cell(e, "Accepted at (UTC)")).truncatedTo(MILLIS))
        .isEqualTo(
            Instant.parse(externalAccepted.get("acceptedAt").asString()).truncatedTo(MILLIS));
    assertThat(Workbook.cell(e, "First name")).isEqualTo("Ana");
    assertThat(Workbook.cell(e, "Last name")).isEqualTo("Novak");
    assertThat(Workbook.cell(e, "Organization / institution")).isEqualTo("Univerza v Mariboru");
    assertThat(Workbook.cell(e, "Study institution")).isEmpty();
    assertThat(Workbook.cell(e, "Study programme")).isEmpty();
    assertThat(Workbook.cell(e, "Student ID")).isEmpty();
    assertThat(Workbook.cell(e, "Options"))
        .isEqualTo(
            "Delavnica: umetna inteligenca v praksi [ws-ai]; Kosilo, 1. dan [meal-lunch-day1]");
    assertThat(Workbook.cell(e, "Consent")).isEqualTo(CONSENT_TEXT);
    assertThat(Instant.parse(Workbook.cell(e, "Consent given at (UTC)"))).isNotNull();

    List<String> s = workbook.rowFor(Registrations.email(student)).orElseThrow();
    assertThat(Workbook.cell(s, "Registration ID")).isEqualTo(idOf(studentAccepted).toString());
    assertThat(Workbook.cell(s, "Type")).isEqualTo("Student");
    assertThat(Workbook.cell(s, "Organization / institution")).isEmpty();
    assertThat(Workbook.cell(s, "Study institution")).isEqualTo("Univerza v Ljubljani");
    assertThat(Workbook.cell(s, "Study programme")).isEqualTo("Računalništvo in informatika");
    assertThat(Workbook.cell(s, "Student ID")).isEqualTo("63210042");
    assertThat(Workbook.cell(s, "Options")).isEmpty();
    assertThat(workbook.rows().indexOf(e)).isLessThan(workbook.rows().indexOf(s));
  }

  @Test
  @DisplayName("AC-008-02 the export without organizer access is refused")
  void ac008_02_exportWithoutAccessIsRefused() {
    ObjectNode registration = Registrations.external();
    registerAccepted(registration);

    ApiClient.Response response = api.export(null);

    assertThat(response.status()).isEqualTo(401);
    assertThat(response.contentType()).doesNotStartWith(ApiClient.XLSX);
    assertThat(response.text()).doesNotContain(Registrations.email(registration));
  }

  @Test
  @DisplayName("AC-008-03 the export with wrong organizer credentials is refused")
  void ac008_03_exportWithWrongCredentialsIsRefused() {
    ObjectNode registration = Registrations.external();
    registerAccepted(registration);

    ApiClient.Response wrongPassword =
        api.token(AcceptanceEnvironment.ORGANIZER_USERNAME, "wrong-password-123");
    assertThat(wrongPassword.status()).isEqualTo(401);
    assertThat(wrongPassword.text()).doesNotContain("token\"");
    ApiClient.Response wrongUser =
        api.token("someone-else", AcceptanceEnvironment.ORGANIZER_PASSWORD);
    assertThat(wrongUser.status()).isEqualTo(401);

    ApiClient.Response forged = api.export("forged-token-value");
    assertThat(forged.status()).isEqualTo(401);
    assertThat(forged.contentType()).doesNotStartWith(ApiClient.XLSX);
    assertThat(forged.text()).doesNotContain(Registrations.email(registration));
  }

  @Test
  @DisplayName("AC-008-04 with no registrations the export holds only the headings")
  void ac008_04_emptyExportHoldsOnlyHeadings() throws Exception {
    String name = "empty_" + UUID.randomUUID().toString().substring(0, 8);
    database.createDatabase(name);
    var properties =
        AcceptanceEnvironment.propertiesFor(
            AcceptanceEnvironment.jdbcUrlOf(name),
            Files.createTempDirectory("json-ac008-04"),
            AcceptanceEnvironment.OPTIONS_FILE);

    try (RunningApp app = RunningApp.start(properties)) {
      ApiClient.Response response = app.api().export(app.api().organizerToken());
      assertThat(response.status()).as(response.text()).isEqualTo(200);
      Workbook workbook = Workbook.parse(response.body());
      assertThat(workbook.headings()).isEqualTo(Workbook.HEADINGS);
      assertThat(workbook.rows()).isEmpty();
    }
  }

  @Test
  @DisplayName("AC-008-05 Slovenian characters appear unchanged in the export")
  void ac008_05_unicodeIsExportedUnchanged() {
    ObjectNode registration = Registrations.external();
    registration.put("firstName", "Čedomir");
    registration.put("lastName", "Šegula Žužek");
    registration.put("organization", "Društvo za ščuke in žabe – ČŠŽ");
    registerAccepted(registration);

    List<String> row = export().rowFor(Registrations.email(registration)).orElseThrow();

    assertThat(Workbook.cell(row, "First name")).isEqualTo("Čedomir");
    assertThat(Workbook.cell(row, "Last name")).isEqualTo("Šegula Žužek");
    assertThat(Workbook.cell(row, "Organization / institution"))
        .isEqualTo("Društvo za ščuke in žabe – ČŠŽ");
  }

  @Test
  @DisplayName("AC-008-06 a new registration appears in the next export")
  void ac008_06_newRegistrationAppearsInNextExport() {
    ObjectNode registration = Registrations.student();
    String email = Registrations.email(registration);
    Workbook before = export();
    assertThat(before.rowFor(email)).isEmpty();

    registerAccepted(registration);

    Workbook after = export();
    assertThat(after.rowFor(email)).isPresent();
    assertThat(after.rows()).hasSize(before.rows().size() + 1);
  }
}
