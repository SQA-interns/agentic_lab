package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.student;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.TestEnvironment;

/** US-008 organizers export the registrations as an Excel workbook. */
class ExportAcceptanceTest extends AcceptanceTest {

  static final List<String> HEADER =
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
          "Consents");

  static List<List<String>> readSheet(byte[] xlsx) throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = wb.getSheet("Registrations");
      assertThat(sheet).as("sheet Registrations").isNotNull();
      List<List<String>> rows = new ArrayList<>();
      for (Row row : sheet) {
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
          Cell c = row.getCell(i);
          if (c != null) {
            assertThat(c.getCellType()).isIn(CellType.STRING, CellType.BLANK);
          }
          cells.add(c == null ? "" : c.getStringCellValue());
        }
        rows.add(cells);
      }
      return rows;
    }
  }

  static List<String> rowFor(List<List<String>> rows, String email) {
    return rows.stream()
        .filter(r -> r.size() > 5 && email.equals(r.get(5)))
        .findFirst()
        .orElse(null);
  }

  @Test
  @DisplayName("AC-008-01 the organizer downloads a workbook with one row per registration")
  void ac008_01_exportsWorkbook() throws IOException {
    Map<String, Object> ext = external();
    ext.put("firstName", "Žan");
    ext.put("lastName", "Čuk Šinkovec");
    ext.put("organization", "=HYPERLINK(\"http://evil.test\")");
    Map<String, Object> stu = student();
    Api.Response r1 = api.register(ext);
    Api.Response r2 = api.register(stu);
    assertThat(r1.status()).as(r1.text()).isEqualTo(201);
    assertThat(r2.status()).as(r2.text()).isEqualTo(201);

    Api.Response response =
        api.export(TestEnvironment.ORGANIZER_USERNAME, TestEnvironment.ORGANIZER_PASSWORD);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.header("Content-Type"))
        .startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertThat(response.header("Content-Disposition"))
        .isEqualTo("attachment; filename=\"registrations.xlsx\"");
    assertThat(response.header("Cache-Control")).contains("no-store");
    List<List<String>> rows = readSheet(response.body());
    assertThat(rows.get(0)).containsExactlyElementsOf(HEADER);
    assertThat(rows).hasSize((int) Database.countRegistrations() + 1);

    List<String> e = rowFor(rows, (String) ext.get("email"));
    assertThat(e).isNotNull();
    assertThat(e.get(0)).isEqualTo(r1.json().get("id").asString());
    assertThat(e.get(2)).isEqualTo("EXTERNAL");
    assertThat(e.get(3)).isEqualTo("Žan");
    assertThat(e.get(4)).isEqualTo("Čuk Šinkovec");
    assertThat(e.get(6)).isEqualTo("=HYPERLINK(\"http://evil.test\")");
    assertThat(e.get(10)).isEqualTo("Delavnica umetne inteligence");
    assertThat(e.get(12)).isEqualTo("Kosilo, dan 1");
    assertThat(e.get(14)).startsWith("privacy (");

    List<String> s = rowFor(rows, (String) stu.get("email"));
    assertThat(s).isNotNull();
    assertThat(s.get(2)).isEqualTo("STUDENT");
    assertThat(s.get(7)).isEqualTo("Univerza v Ljubljani");
    assertThat(s.get(8)).isEqualTo("Računalništvo in informatika");
    assertThat(s.get(9)).isEqualTo("63210001");
    assertThat(s.get(11)).isEqualTo("Conference dinner");
    assertThat(s.get(13)).isEqualTo("Ogled Ljubljane");
    assertThat(s.get(14)).contains("privacy (").contains("newsletter (");
  }

  @Test
  @DisplayName("AC-008-02 the export without credentials is refused with 401")
  void ac008_02_refusesWithoutCredentials() {
    Map<String, Object> ext = external();
    assertThat(api.register(ext).status()).isEqualTo(201);

    Api.Response response = api.export(null, null);

    assertThat(response.status()).isEqualTo(401);
    assertThat(response.header("WWW-Authenticate")).startsWith("Basic");
    assertThat(response.text()).doesNotContain((String) ext.get("email"));
  }

  @Test
  @DisplayName("AC-008-02 the export with wrong credentials is refused with 401")
  void ac008_02_refusesWrongCredentials() {
    Map<String, Object> ext = external();
    assertThat(api.register(ext).status()).isEqualTo(201);

    Api.Response wrongPassword = api.export(TestEnvironment.ORGANIZER_USERNAME, "wrong-password");
    Api.Response wrongUser = api.export("someone", TestEnvironment.ORGANIZER_PASSWORD);

    assertThat(wrongPassword.status()).isEqualTo(401);
    assertThat(wrongUser.status()).isEqualTo(401);
    assertThat(wrongPassword.text()).doesNotContain((String) ext.get("email"));
  }
}
