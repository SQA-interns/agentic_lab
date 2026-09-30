package lab.conference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import lab.conference.acceptance.support.Api;
import lab.conference.acceptance.support.AppInstance;
import lab.conference.acceptance.support.Payloads;
import lab.conference.acceptance.support.Workbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** US-008: authenticated organizer Excel export. */
class ExportAcceptanceTest {

  private static final List<String> HEADERS =
      List.of(
          "Registration ID",
          "Form type",
          "Accepted at (UTC)",
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
          "Consent",
          "Client request ID");
  private static AppInstance app;
  private static Api api;

  @BeforeAll
  static void start() {
    app = AppInstance.builder().build().start();
    api = app.api();
  }

  @AfterAll
  static void stop() {
    app.stop();
  }

  private Workbook export() {
    Api.Response r = api.export(app.organizerUser(), app.organizerPassword());
    assertThat(r.status()).as(r.toString()).isEqualTo(200);
    assertThat(r.header("Content-Type"))
        .startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertThat(r.header("Content-Disposition")).contains("attachment").contains(".xlsx");
    assertThat(r.header("Cache-Control")).contains("no-store");
    return new Workbook(r.bytes());
  }

  @Test
  void ac_008_01_exportContainsAcceptedRegistrationsFromBothForms() {
    Map<String, Object> external = Payloads.external();
    external.put(
        "selections",
        Payloads.selections(
            List.of("ws-alpha", "ws-beta"), List.of(), List.of("meal-veg"), List.of()));
    Map<String, Object> student = Payloads.student();
    String externalId = api.postExternal(external).json().path("registrationId").asText();
    String studentId = api.postStudent(student).json().path("registrationId").asText();

    Workbook wb = export();

    assertThat(wb.sheetName()).isEqualTo("Registrations");
    assertThat(wb.headers()).containsExactlyElementsOf(HEADERS);
    Map<String, Cell> e = wb.row(externalId);
    assertThat(e).as("external row").isNotNull();
    assertThat(Workbook.text(e.get("Form type"))).isEqualTo("external");
    assertThat(Workbook.text(e.get("First name"))).isEqualTo("Ana");
    assertThat(Workbook.text(e.get("Last name"))).isEqualTo("Kovač");
    assertThat(Workbook.text(e.get("Email"))).isEqualTo(external.get("email"));
    assertThat(Workbook.text(e.get("Organization / institution")))
        .isEqualTo("Synthetic Institute d.o.o.");
    assertThat(Workbook.text(e.get("Student ID"))).isEmpty();
    assertThat(Workbook.text(e.get("Workshops")))
        .isEqualTo("Workshop Alpha – Čebelarstvo; Workshop Beta");
    assertThat(Workbook.text(e.get("Meals"))).isEqualTo("Vegetarian lunch");
    assertThat(Workbook.text(e.get("Consent"))).isEqualTo("yes");
    assertThat(Workbook.text(e.get("Client request ID")))
        .isEqualTo(external.get("clientRequestId"));
    assertThat(Workbook.text(e.get("Accepted at (UTC)"))).isNotBlank();

    Map<String, Cell> s = wb.row(studentId);
    assertThat(s).as("student row").isNotNull();
    assertThat(Workbook.text(s.get("Form type"))).isEqualTo("student");
    assertThat(Workbook.text(s.get("Study institution"))).isEqualTo("Synthetic University");
    assertThat(Workbook.text(s.get("Study programme"))).isEqualTo("Computer Science");
    assertThat(Workbook.text(s.get("Student ID"))).isEqualTo("S-12345/Č");
    assertThat(Workbook.text(s.get("Organization / institution"))).isEmpty();
    assertThat(Workbook.text(s.get("Events"))).isEqualTo("Gala evening");
  }

  @Test
  void ac_008_02_missingOrWrongCredentialsAreRefusedWithoutData() {
    Map<String, Object> body = Payloads.external();
    assertThat(api.postExternal(body).status()).isEqualTo(201);
    String email = (String) body.get("email");

    for (Api.Response r :
        List.of(
            api.export(null, null),
            api.export(app.organizerUser(), "wrong-password"),
            api.export("intruder", app.organizerPassword()),
            api.export(app.organizerUser(), ""))) {
      assertThat(r.status()).as(r.toString()).isEqualTo(401);
      assertThat(r.header("WWW-Authenticate")).startsWith("Basic");
      assertThat(r.text()).doesNotContain(email, "Kovač");
      assertThat(r.header("Content-Type")).doesNotContain("spreadsheet");
    }
  }

  @Test
  void ac_008_03_formulaLikeTextIsExportedAsPlainText() {
    Map<String, Object> body = Payloads.external();
    body.put("firstName", "=HYPERLINK(\"http://evil.test\",\"x\")");
    body.put("lastName", "+1+1");
    body.put("organization", "@SUM(A1:A2)");
    Map<String, Object> student = Payloads.student();
    student.put("studentId", "-2+3");
    String id = api.postExternal(body).json().path("registrationId").asText();
    String sid = api.postStudent(student).json().path("registrationId").asText();

    Workbook wb = export();

    Map<String, Cell> row = wb.row(id);
    for (String[] expected :
        new String[][] {
          {"First name", "=HYPERLINK(\"http://evil.test\",\"x\")"},
          {"Last name", "+1+1"},
          {"Organization / institution", "@SUM(A1:A2)"}
        }) {
      Cell cell = row.get(expected[0]);
      assertThat(cell.getCellType()).as(expected[0]).isEqualTo(CellType.STRING);
      assertThat(cell.getStringCellValue()).isEqualTo(expected[1]);
      assertThat(cell.getCellStyle().getQuotePrefixed()).as(expected[0] + " quote prefix").isTrue();
    }
    Cell studentCell = wb.row(sid).get("Student ID");
    assertThat(studentCell.getCellType()).isEqualTo(CellType.STRING);
    assertThat(studentCell.getStringCellValue()).isEqualTo("-2+3");
  }

  @Test
  void ac_008_04_rejectedSubmissionsDoNotAppearInExport() {
    Map<String, Object> rejected = Payloads.external();
    rejected.put("consentGiven", false);
    String rejectedEmail = (String) rejected.get("email");
    assertThat(api.postExternal(rejected).status()).isEqualTo(400);
    Map<String, Object> badOption = Payloads.student();
    badOption.put(
        "selections", Payloads.selections(List.of("ws-inactive"), List.of(), List.of(), List.of()));
    assertThat(api.postStudent(badOption).status()).isEqualTo(400);

    Workbook wb = export();

    assertThat(wb.containsText(rejectedEmail)).isFalse();
    assertThat(wb.containsText((String) badOption.get("email"))).isFalse();
    assertThat(wb.rows()).hasSize((int) app.store().registrationCount());
  }
}
