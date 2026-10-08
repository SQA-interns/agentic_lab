package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.student;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceStack;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Response;

/** US-008 Registration export as an Excel workbook, organizer access only. */
class Us008ExportAcceptanceTest {

  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private final Api api = AcceptanceStack.shared().api();

  @Test
  @DisplayName("AC-008-01 the organizer exports one row per registration with all data")
  void ac008_01_organizerExportsAllRegistrations() {
    Map<String, Object> ext = external();
    Map<String, Object> stu = student();
    assertThat(api.register(ext).status()).isEqualTo(201);
    assertThat(api.register(stu).status()).isEqualTo(201);

    Response response = api.exportAsOrganizer();

    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    assertThat(response.contentType()).startsWith(XLSX);
    assertThat(response.headers().firstValue("Content-Disposition").orElse(""))
        .contains("attachment");
    Map<String, Map<String, String>> rows = ExportReader.rowsByEmail(response.body());
    assertThat(rows.size()).isEqualTo(AcceptanceStack.db().countRegistrations());

    Map<String, String> externalRow = rows.get((String) ext.get("email"));
    assertThat(externalRow).as("external row").isNotNull();
    assertThat(externalRow.values())
        .anyMatch(v -> v.toUpperCase().contains("EXTERNAL"))
        .contains("Ana", "Novak", "Institut Primer")
        .anyMatch(v -> v.contains("Delavnica: testiranje programske opreme"))
        .anyMatch(v -> v.contains("Welcome reception"))
        .anyMatch(v -> v.contains("Conference dinner (vegetarian option)"))
        .anyMatch(v -> v.contains("data-processing"));

    Map<String, String> studentRow = rows.get((String) stu.get("email"));
    assertThat(studentRow).as("student row").isNotNull();
    assertThat(studentRow.values())
        .anyMatch(v -> v.toUpperCase().contains("STUDENT"))
        .contains("Luka", "Horvat", "Univerza v Mariboru", "Informatika", "E1234567")
        .anyMatch(v -> v.contains("Career fair"))
        .anyMatch(v -> v.contains("Lunch, day 1"))
        .anyMatch(v -> v.contains("data-processing"));
  }

  @Test
  @DisplayName("AC-008-02 the export without organizer credentials is refused")
  void ac008_02_exportWithoutCredentialsIsRefused() {
    Map<String, Object> ext = external();
    assertThat(api.register(ext).status()).isEqualTo(201);

    Response response = api.export(null, null);

    assertThat(response.status()).as(response.toString()).isEqualTo(401);
    assertThat(response.contentType()).doesNotStartWith(XLSX);
    assertThat(response.text()).doesNotContain((String) ext.get("email"));
  }

  @Test
  @DisplayName("AC-008-03 the export with wrong organizer credentials is refused")
  void ac008_03_exportWithWrongCredentialsIsRefused() {
    Map<String, Object> ext = external();
    assertThat(api.register(ext).status()).isEqualTo(201);

    Response wrongPassword =
        api.export(AcceptanceStack.ORGANIZER_USERNAME, "wrong-password-123456");
    Response wrongUser = api.export("someone", AcceptanceStack.ORGANIZER_PASSWORD);

    for (Response response : new Response[] {wrongPassword, wrongUser}) {
      assertThat(response.status()).as(response.toString()).isEqualTo(401);
      assertThat(response.contentType()).doesNotStartWith(XLSX);
      assertThat(response.text()).doesNotContain((String) ext.get("email"));
    }
  }

  @Test
  @DisplayName("AC-008-04 Slovenian characters appear unchanged in the export")
  void ac008_04_unicodeSurvivesExport() {
    Map<String, Object> ext = external();
    ext.put("firstName", "Črtomir");
    ext.put("lastName", "Žužek");
    ext.put("organization", "Šola za čebelarstvo");
    assertThat(api.register(ext).status()).isEqualTo(201);

    Response response = api.exportAsOrganizer();

    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    Map<String, String> row =
        ExportReader.rowsByEmail(response.body()).get((String) ext.get("email"));
    assertThat(row).isNotNull();
    assertThat(row.values()).contains("Črtomir", "Žužek", "Šola za čebelarstvo");
    assertThat(ExportReader.hasNoFormulas(response.body())).isTrue();
  }
}
