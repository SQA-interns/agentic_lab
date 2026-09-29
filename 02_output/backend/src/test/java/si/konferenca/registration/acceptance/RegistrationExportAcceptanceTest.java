package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Fixtures;
import si.konferenca.registration.acceptance.support.TestInfrastructure;

/** US-008 registration export for organizers (BR-08, SR-06, SR-07, NFR-01). */
class RegistrationExportAcceptanceTest extends AcceptanceTestBase {

  private static final String EXPORT = "/api/admin/registrations/export";
  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private static final List<String> COLUMNS =
      List.of(
          "Reference",
          "Submitted at",
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
          "Other",
          "Consents");

  private static String organizerAuth() {
    return Api.basicAuth(
        TestInfrastructure.ORGANIZER_USERNAME, TestInfrastructure.ORGANIZER_PASSWORD);
  }

  @Test
  @DisplayName("AC-008-01 an organizer exports all registrations as an Excel workbook")
  void ac00801OrganizerExportsWorkbook() throws IOException {
    String externalEmail = Fixtures.uniqueEmail();
    String studentEmail = Fixtures.uniqueEmail();
    String externalRef = register(Fixtures.external(externalEmail));
    String studentRef = register(Fixtures.student(studentEmail));

    Api.Response r = api.get(EXPORT, "Authorization", organizerAuth());

    assertThat(r.status()).as(r.text()).isEqualTo(200);
    assertThat(r.header("Content-Type")).startsWith(XLSX);
    assertThat(r.header("Content-Disposition")).contains("attachment").contains(".xlsx");
    List<Map<String, String>> rows = rows(r.body());
    Map<String, String> external = rowByEmail(rows, externalEmail);
    assertThat(external.get("Reference")).isEqualTo(externalRef);
    assertThat(external.get("Type")).isEqualTo("EXTERNAL");
    assertThat(external.get("First name")).isEqualTo("Ana");
    assertThat(external.get("Last name")).isEqualTo("Novak");
    assertThat(external.get("Organization")).isEqualTo("Institut Jožef Stefan");
    assertThat(external.get("Workshops")).contains("Workshop: Secure web development");
    assertThat(external.get("Meals")).contains("Lunch, day 1");
    assertThat(external.get("Consents")).contains("data-processing");
    assertThat(external.get("Submitted at")).isNotBlank();
    Map<String, String> student = rowByEmail(rows, studentEmail);
    assertThat(student.get("Reference")).isEqualTo(studentRef);
    assertThat(student.get("Type")).isEqualTo("STUDENT");
    assertThat(student.get("Study institution")).isEqualTo("Univerza v Ljubljani");
    assertThat(student.get("Study programme")).isEqualTo("Računalništvo in informatika");
    assertThat(student.get("Student ID")).isEqualTo("63200001");
    assertThat(student.get("Events")).contains("Student career fair");
  }

  @Test
  @DisplayName("AC-008-02 the export is refused without credentials")
  void ac00802ExportWithoutCredentialsIsRefused() {
    String email = Fixtures.uniqueEmail();
    register(Fixtures.external(email));

    Api.Response r = api.get(EXPORT);

    assertThat(r.status()).isEqualTo(401);
    assertThat(r.text()).doesNotContain(email);
    assertThat(r.header("Content-Type")).doesNotStartWith(XLSX);
  }

  @Test
  @DisplayName("AC-008-02 the export is refused with wrong credentials")
  void ac00802ExportWithWrongCredentialsIsRefused() {
    String email = Fixtures.uniqueEmail();
    register(Fixtures.external(email));

    Api.Response wrongPassword =
        api.get(
            EXPORT,
            "Authorization",
            Api.basicAuth(TestInfrastructure.ORGANIZER_USERNAME, "wrong-password-123456"));
    Api.Response wrongUser =
        api.get(
            EXPORT,
            "Authorization",
            Api.basicAuth("someone", TestInfrastructure.ORGANIZER_PASSWORD));

    assertThat(wrongPassword.status()).isEqualTo(401);
    assertThat(wrongUser.status()).isEqualTo(401);
    assertThat(wrongPassword.text()).doesNotContain(email);
    assertThat(wrongUser.text()).doesNotContain(email);
  }

  @Test
  @DisplayName("AC-008-03 Slovenian characters are exported unchanged (NFR-01)")
  void ac00803UnicodeIsExportedUnchanged() throws IOException {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("firstName", "Živa");
    request.put("lastName", "Čeh Šušteršič");
    request.put("organization", "Zavod Škofja Loka");
    register(request);

    Api.Response r = api.get(EXPORT, "Authorization", organizerAuth());

    assertThat(r.status()).isEqualTo(200);
    Map<String, String> row = rowByEmail(rows(r.body()), email);
    assertThat(row.get("First name")).isEqualTo("Živa");
    assertThat(row.get("Last name")).isEqualTo("Čeh Šušteršič");
    assertThat(row.get("Organization")).isEqualTo("Zavod Škofja Loka");
  }

  @Test
  @DisplayName("AC-008-04 the workbook contains only registration data columns")
  void ac00804WorkbookContainsOnlyRegistrationData() throws IOException {
    register(Fixtures.external(Fixtures.uniqueEmail()));

    Api.Response r = api.get(EXPORT, "Authorization", organizerAuth());

    assertThat(r.status()).isEqualTo(200);
    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(r.body()))) {
      assertThat(wb.getNumberOfSheets()).isEqualTo(1);
      Sheet sheet = wb.getSheet("Registrations");
      assertThat(sheet).isNotNull();
      assertThat(cells(sheet.getRow(0))).containsExactlyElementsOf(COLUMNS);
    }
  }

  @Test
  @DisplayName("AC-008-01 SR-06 organizer credentials are refused over plain HTTP from outside")
  void sr06CredentialsOverPlainHttpFromRemoteClientAreRefused() {
    Api.Response plain =
        api.get(
            EXPORT,
            "Authorization",
            organizerAuth(),
            "X-Forwarded-For",
            "203.0.113.7",
            "X-Forwarded-Proto",
            "http");
    Api.Response https =
        api.get(
            EXPORT,
            "Authorization",
            organizerAuth(),
            "X-Forwarded-For",
            "203.0.113.7",
            "X-Forwarded-Proto",
            "https");

    assertThat(plain.status()).isEqualTo(403);
    assertThat(plain.header("Content-Type")).doesNotStartWith(XLSX);
    assertThat(https.status()).isEqualTo(200);
  }

  private String register(Map<String, Object> request) {
    Api.Response r = api.register(request);
    assertThat(r.status()).as(r.text()).isEqualTo(201);
    return r.json().path("reference").asString();
  }

  private static List<Map<String, String>> rows(byte[] xlsx) throws IOException {
    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = wb.getSheet("Registrations");
      assertThat(sheet).as("sheet Registrations").isNotNull();
      List<String> header = cells(sheet.getRow(0));
      List<Map<String, String>> result = new ArrayList<>();
      for (int i = 1; i <= sheet.getLastRowNum(); i++) {
        List<String> values = cells(sheet.getRow(i));
        Map<String, String> row = new LinkedHashMap<>();
        for (int c = 0; c < header.size(); c++) {
          row.put(header.get(c), c < values.size() ? values.get(c) : "");
        }
        result.add(row);
      }
      return result;
    }
  }

  private static List<String> cells(Row row) {
    DataFormatter f = new DataFormatter();
    List<String> values = new ArrayList<>();
    if (row == null) {
      return values;
    }
    for (int c = 0; c < row.getLastCellNum(); c++) {
      Cell cell = row.getCell(c);
      values.add(cell == null ? "" : f.formatCellValue(cell));
    }
    return values;
  }

  private static Map<String, String> rowByEmail(List<Map<String, String>> rows, String email) {
    List<Map<String, String>> matches =
        rows.stream().filter(r -> email.equals(r.get("Email"))).toList();
    assertThat(matches).as("rows for " + email).hasSize(1);
    return matches.get(0);
  }
}
