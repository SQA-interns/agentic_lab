package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.Test;

/** US-008 — Registration export (specification §5.2, §7). */
class ExportAcceptanceTest extends AcceptanceTestBase {

  private static final List<String> HEADERS =
      List.of(
          "Registration ID",
          "Submitted at (UTC)",
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
          "Personal data consent at (UTC)");

  private static List<String> rowFor(List<List<String>> rows, String id) {
    return rows.stream()
        .filter(x -> x.get(0).equals(id))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no export row for " + id));
  }

  @Test
  void ac_008_01_organizerCanExportRegistrationsAsExcel() {
    String extId =
        register(validExternal(uniqueEmail("ac00801a"))).json().path("registrationId").asText();
    String stuId =
        register(validStudent(uniqueEmail("ac00801b"))).json().path("registrationId").asText();

    Resp r = exportAsOrganizer();

    assertThat(r.status()).isEqualTo(200);
    assertThat(r.header("Content-Type"))
        .startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertThat(r.header("Content-Disposition"))
        .startsWith("attachment")
        .containsPattern("filename=\"registrations-\\d{8}-\\d{6}\\.xlsx\"");
    assertThat(r.header("Cache-Control")).contains("no-store");
    List<List<String>> rows = ExcelReader.rows(r.body());
    assertThat(rows.get(0)).containsExactlyElementsOf(HEADERS);
    assertThat(rows.stream().map(x -> x.get(0))).contains(extId, stuId);
    long occurrences = rows.stream().filter(x -> x.get(0).equals(extId)).count();
    assertThat(occurrences).isEqualTo(1);
  }

  @Test
  void ac_008_02_exportContainsAllRegistrationDataPerType() {
    String extEmail = uniqueEmail("ac00802a");
    String stuEmail = uniqueEmail("ac00802b");
    String extId =
        register(validExternal(extEmail, "ws-ai", "ws-sec", "meal-lunch1"))
            .json()
            .path("registrationId")
            .asText();
    String stuId =
        register(validStudent(stuEmail, "ev-dinner", "other-tour"))
            .json()
            .path("registrationId")
            .asText();

    List<List<String>> rows = ExcelReader.rows(exportAsOrganizer().body());

    List<String> ext = rowFor(rows, extId);
    assertThat(ext.get(1)).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
    assertThat(ext.get(2)).isEqualTo("External");
    assertThat(ext.get(3)).isEqualTo("Ana");
    assertThat(ext.get(4)).isEqualTo("Novak");
    assertThat(ext.get(5)).isEqualTo(extEmail);
    assertThat(ext.get(6)).isEqualTo("Institut Jožef Stefan");
    assertThat(ext.subList(7, 10)).containsOnly("");
    assertThat(ext.get(10)).isEqualTo("Delavnica: umetna inteligenca; Varnost spletnih aplikacij");
    assertThat(ext.get(11)).isEmpty();
    assertThat(ext.get(12)).isEqualTo("Kosilo, 1. dan");
    assertThat(ext.get(13)).isEmpty();
    assertThat(ext.get(14)).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");

    List<String> stu = rowFor(rows, stuId);
    assertThat(stu.get(2)).isEqualTo("Student");
    assertThat(stu.get(6)).isEmpty();
    assertThat(stu.get(7)).isEqualTo("Fakulteta za računalništvo in informatiko");
    assertThat(stu.get(8)).isEqualTo("Računalništvo in informatika");
    assertThat(stu.get(9)).isEqualTo("63200001");
    assertThat(stu.get(10)).isEmpty();
    assertThat(stu.get(11)).isEqualTo("Conference dinner");
    assertThat(stu.get(13)).isEqualTo("Ogled Ljubljane (city tour)");
  }

  @Test
  void ac_008_02_participantTextIsWrittenAsTextNotFormula() {
    String email = uniqueEmail("ac00802c");
    ObjectNode body = validExternal(email);
    body.put("organization", "=HYPERLINK(\"http://evil.example\",\"x\")");
    String id = register(body).json().path("registrationId").asText();

    byte[] xlsx = exportAsOrganizer().body();
    List<List<String>> rows = ExcelReader.rows(xlsx);
    int index = rows.indexOf(rowFor(rows, id));

    assertThat(rows.get(index).get(6)).isEqualTo("=HYPERLINK(\"http://evil.example\",\"x\")");
    assertThat(ExcelReader.typeOf(xlsx, index, 6)).isEqualTo(CellType.STRING);
  }

  @Test
  void ac_008_03_exportIsCurrent() {
    List<List<String>> before = ExcelReader.rows(exportAsOrganizer().body());
    String id =
        register(validExternal(uniqueEmail("ac00803"))).json().path("registrationId").asText();

    List<List<String>> after = ExcelReader.rows(exportAsOrganizer().body());

    assertThat(before.stream().map(x -> x.get(0))).doesNotContain(id);
    assertThat(after.stream().map(x -> x.get(0))).contains(id);
    assertThat(after).hasSize(before.size() + 1);
  }

  @Test
  void ac_008_04_exportPreservesUnicode() {
    ObjectNode body = validExternal(uniqueEmail("ac00804"));
    body.put("firstName", "Živa");
    body.put("lastName", "Čepič-Šuštaršič");
    body.put("organization", "Društvo za ćevapčiće in đuveč");
    String id = register(body).json().path("registrationId").asText();

    List<String> row = rowFor(ExcelReader.rows(exportAsOrganizer().body()), id);

    assertThat(row.get(3)).isEqualTo("Živa");
    assertThat(row.get(4)).isEqualTo("Čepič-Šuštaršič");
    assertThat(row.get(6)).isEqualTo("Društvo za ćevapčiće in đuveč");
  }

  @Test
  void ac_008_05_exportWithNoRegistrationsHasOnlyHeaders() throws SQLException {
    try (Connection c = db();
        PreparedStatement delOptions = c.prepareStatement("delete from registration_option");
        PreparedStatement delRegs = c.prepareStatement("delete from registration")) {
      delOptions.executeUpdate();
      delRegs.executeUpdate();
    }

    Resp r = exportAsOrganizer();

    assertThat(r.status()).isEqualTo(200);
    List<List<String>> rows = ExcelReader.rows(r.body());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsExactlyElementsOf(HEADERS);
  }

  @Test
  void ac_008_06_exportWithoutCredentialsIsRefused() {
    String email = uniqueEmail("ac00806a");
    register(validExternal(email));

    Resp r = get("/api/organizer/registrations/export");

    assertThat(r.status()).isEqualTo(401);
    assertThat(r.header("WWW-Authenticate")).startsWith("Basic");
    assertThat(r.text()).doesNotContain(email).doesNotContain("Novak");
  }

  @Test
  void ac_008_06_exportWithWrongCredentialsIsRefused() {
    String email = uniqueEmail("ac00806b");
    register(validExternal(email));

    Resp wrongPassword =
        getAsOrganizer("/api/organizer/registrations/export", ORGANIZER_USER, "wrong-password");
    Resp wrongUser =
        getAsOrganizer("/api/organizer/registrations/export", "someone", ORGANIZER_PASSWORD);

    assertThat(wrongPassword.status()).isEqualTo(401);
    assertThat(wrongPassword.text()).doesNotContain(email);
    assertThat(wrongUser.status()).isEqualTo(401);
    assertThat(wrongUser.text()).doesNotContain(email);
  }
}
