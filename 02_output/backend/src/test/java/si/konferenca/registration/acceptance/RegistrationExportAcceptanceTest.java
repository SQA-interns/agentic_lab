package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.student;

import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.RunningApp.Response;
import si.konferenca.registration.acceptance.support.Workbook;

/** US-008 Registration export (openapi.yaml exportRegistrations, BR-08). */
class RegistrationExportAcceptanceTest {

  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private static void assertRefusedWithoutData(Response r, String... secrets) {
    assertThat(r.status()).isEqualTo(401);
    assertThat(r.header("Content-Type")).doesNotContain("spreadsheetml");
    for (String s : secrets) {
      assertThat(r.text()).doesNotContain(s);
    }
  }

  @Test
  void AC_008_01_organizerExportsOneRowPerRegistrationWithAllData() {
    try (RunningApp app = RunningApp.start()) {
      Map<String, Object> ext = external();
      Map<String, Object> stu = student();
      Response r1 = app.register(ext);
      Response r2 = app.register(stu);
      assertThat(r1.status()).isEqualTo(201);
      assertThat(r2.status()).isEqualTo(201);

      Response r = app.exportAsOrganizer();

      assertThat(r.status()).isEqualTo(200);
      assertThat(r.header("Content-Type")).startsWith(XLSX);
      assertThat(r.header("Content-Disposition")).contains("attachment").contains(".xlsx");
      Workbook wb = new Workbook(r.body());
      assertThat(wb.dataRows()).hasSize(2);
      assertThat(wb.header()).allSatisfy(h -> assertThat(h).isNotBlank());

      List<String> e = wb.rowContaining((String) ext.get("email"));
      assertThat(e).isNotNull();
      assertThat(e).anySatisfy(c -> assertThat(c).isEqualToIgnoringCase("external"));
      assertThat(e).contains("Ana", "Novak", "Institut Jožef Stefan");
      assertThat(String.join("|", e)).contains("AI workshop").contains("Lunch");
      String date1 = r1.json().path("receivedAt").asString().substring(0, 10);
      assertThat(e).anySatisfy(c -> assertThat(c).contains(date1));

      List<String> s = wb.rowContaining((String) stu.get("email"));
      assertThat(s).isNotNull();
      assertThat(s).anySatisfy(c -> assertThat(c).isEqualToIgnoringCase("student"));
      assertThat(s).contains("Luka", "Kranjc", "Univerza v Mariboru", "Informatika", "93120001");
      assertThat(String.join("|", s)).contains("Security workshop").contains("City tour");
    }
  }

  @Test
  void AC_008_02_exportWithoutCredentialsIsRefused() {
    try (RunningApp app = RunningApp.start()) {
      Map<String, Object> reg = external();
      assertThat(app.register(reg).status()).isEqualTo(201);

      Response r = app.export(null, null);

      assertRefusedWithoutData(r, (String) reg.get("email"), "Novak");
    }
  }

  @Test
  void AC_008_03_exportWithWrongCredentialsIsRefused() {
    try (RunningApp app = RunningApp.start()) {
      Map<String, Object> reg = external();
      assertThat(app.register(reg).status()).isEqualTo(201);

      Response wrongPassword =
          app.export(AcceptanceEnvironment.ORGANIZER_USERNAME, "wrong-password");
      Response wrongUser = app.export("admin", AcceptanceEnvironment.ORGANIZER_PASSWORD);

      assertRefusedWithoutData(wrongPassword, (String) reg.get("email"));
      assertRefusedWithoutData(wrongUser, (String) reg.get("email"));
    }
  }

  @Test
  void AC_008_04_emptyExportHasOnlyHeaderRow() {
    try (RunningApp app = RunningApp.start()) {
      Response r = app.exportAsOrganizer();

      assertThat(r.status()).isEqualTo(200);
      Workbook wb = new Workbook(r.body());
      assertThat(wb.rowCount()).isEqualTo(1);
      assertThat(wb.header()).isNotEmpty();
    }
  }

  @Test
  void AC_008_05_slovenianCharactersAreUnchangedInExport() {
    try (RunningApp app = RunningApp.start()) {
      Map<String, Object> reg = external();
      reg.put("firstName", "Žiga");
      reg.put("lastName", "Čučnik Šumenjak");
      reg.put("organization", "Občina Škofja Loka");
      assertThat(app.register(reg).status()).isEqualTo(201);

      Workbook wb = new Workbook(app.exportAsOrganizer().body());

      assertThat(wb.rowContaining((String) reg.get("email")))
          .contains("Žiga", "Čučnik Šumenjak", "Občina Škofja Loka");
    }
  }

  @Test
  void AC_008_06_formulaLikeValuesAreTextNotFormulas() {
    try (RunningApp app = RunningApp.start()) {
      Map<String, Object> reg = external();
      reg.put("firstName", "=1+1");
      reg.put("lastName", "+SUM(A1:A2)");
      reg.put("organization", "@HYPERLINK(\"http://example.com\")");
      assertThat(app.register(reg).status()).isEqualTo(201);

      Workbook wb = new Workbook(app.exportAsOrganizer().body());
      String email = (String) reg.get("email");

      assertThat(wb.rowContaining(email))
          .contains("=1+1", "+SUM(A1:A2)", "@HYPERLINK(\"http://example.com\")");
      assertThat(wb.typesOfRowContaining(email)).doesNotContain(CellType.FORMULA);
    }
  }
}
