package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** US-008 Registration export, through the REST contract. */
class Us008ExportAcceptanceTest extends AcceptanceTestBase {

  private static final String EXPORT = "/api/registrations/export";
  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private static Api.Reply exportAsOrganizer() {
    return Api.getAsOrganizer(
        Stack.app(), EXPORT, Stack.ORGANIZER_USERNAME, Stack.ORGANIZER_PASSWORD);
  }

  private static List<String> rowOf(List<List<String>> rows, UUID id) {
    List<List<String>> matching = rows.stream().filter(row -> row.contains(id.toString())).toList();
    assertThat(matching).as("rows of registration %s", id).hasSize(1);
    return matching.get(0);
  }

  @Test
  void ac_008_01_organizerExportsEveryRegistrationAsAWorkbook() {
    UUID externalId = assertAccepted(Api.register(Stack.app(), external()));
    UUID studentId = assertAccepted(Api.register(Stack.app(), student()));

    Api.Reply reply = exportAsOrganizer();

    assertThat(reply.status()).isEqualTo(200);
    assertThat(reply.header("Content-Type")).startsWith(XLSX);
    assertThat(reply.header("Content-Disposition")).startsWith("attachment").contains(".xlsx");
    assertThat(Workbooks.sheetCount(reply.body())).isEqualTo(1);
    List<List<String>> rows = Workbooks.rowsOf(reply.body());
    assertThat(rows).as("heading row and two registrations").hasSize(3);
    assertThat(rowOf(rows, externalId))
        .contains("EXTERNAL", "Ana", "Novak", "ana.novak@example.org", "Podjetje Primer")
        .anyMatch(cell -> cell.contains("Delavnica: testiranje programske opreme"))
        .anyMatch(cell -> cell.contains("Kosilo, prvi dan"));
    assertThat(rowOf(rows, studentId))
        .contains(
            "STUDENT",
            "Luka",
            "Kovač",
            "luka.kovac@example.org",
            "Univerza v Ljubljani",
            "Računalništvo in informatika",
            "63210001")
        .anyMatch(cell -> cell.contains("Otvoritvena slovesnost"))
        .anyMatch(cell -> cell.contains("Voden ogled mesta"));
  }

  @Test
  void ac_008_02_exportWithoutOrganizerAccessIsRefused() {
    assertAccepted(Api.register(Stack.app(), external()));

    Api.Reply reply = Api.get(Stack.app(), EXPORT);

    assertThat(reply.status()).isEqualTo(401);
    assertThat(reply.header("Content-Type")).doesNotStartWith(XLSX);
    assertThat(reply.text()).doesNotContain("ana.novak@example.org", "Novak");
  }

  @Test
  void ac_008_03_exportWithWrongCredentialsIsRefused() {
    assertAccepted(Api.register(Stack.app(), external()));

    List<Api.Reply> replies =
        List.of(
            Api.getAsOrganizer(Stack.app(), EXPORT, Stack.ORGANIZER_USERNAME, "wrong-password"),
            Api.getAsOrganizer(Stack.app(), EXPORT, "someone-else", Stack.ORGANIZER_PASSWORD),
            Api.getAsOrganizer(Stack.app(), EXPORT, Stack.ORGANIZER_USERNAME, ""));

    for (Api.Reply reply : replies) {
      assertThat(reply.status()).isEqualTo(401);
      assertThat(reply.header("Content-Type")).doesNotStartWith(XLSX);
      assertThat(reply.text()).doesNotContain("ana.novak@example.org", "Novak");
    }
  }

  @Test
  void ac_008_04_exportContainsRegistrationsAcceptedAfterAnEarlierExport() {
    UUID first = assertAccepted(Api.register(Stack.app(), external()));
    Api.Reply earlier = exportAsOrganizer();
    assertThat(earlier.status()).isEqualTo(200);
    assertThat(Workbooks.rowsOf(earlier.body())).hasSize(2);

    UUID second = assertAccepted(Api.register(Stack.app(), student()));
    Api.Reply later = exportAsOrganizer();

    assertThat(later.status()).isEqualTo(200);
    List<List<String>> rows = Workbooks.rowsOf(later.body());
    assertThat(rows).hasSize(3);
    assertThat(rowOf(rows, first)).contains("ana.novak@example.org");
    assertThat(rowOf(rows, second)).contains("luka.kovac@example.org");
  }

  @Test
  void ac_008_05_exportShowsSlovenianCharactersUnchanged() {
    Map<String, Object> registration = external();
    registration.put("firstName", "Živa");
    registration.put("lastName", "Čučnik Šušteršič");
    registration.put("organization", "Inštitut za računalništvo Žalec");
    registration.put("optionIds", List.of("ev-dinner"));
    UUID id = assertAccepted(Api.register(Stack.app(), registration));

    Api.Reply reply = exportAsOrganizer();

    assertThat(reply.status()).isEqualTo(200);
    assertThat(rowOf(Workbooks.rowsOf(reply.body()), id))
        .contains("Živa", "Čučnik Šušteršič", "Inštitut za računalništvo Žalec")
        .anyMatch(cell -> cell.contains("Slavnostna večerja"));
  }

  @Test
  void ac_008_06_exportWithoutRegistrationsHasOnlyTheHeadings() {
    Api.Reply reply = exportAsOrganizer();

    assertThat(reply.status()).isEqualTo(200);
    assertThat(reply.header("Content-Type")).startsWith(XLSX);
    List<List<String>> rows = Workbooks.rowsOf(reply.body());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).hasSizeGreaterThanOrEqualTo(10).noneMatch(String::isBlank);
  }
}
