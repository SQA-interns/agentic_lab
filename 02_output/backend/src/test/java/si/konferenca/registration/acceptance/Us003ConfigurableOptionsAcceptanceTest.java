package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US-003 Configurable conference options, through the REST and configuration contracts. */
class Us003ConfigurableOptionsAcceptanceTest extends AcceptanceTestBase {

  private static Stack.App changedApp;

  /** A backend started with the changed options file: one added, one renamed, two deactivated. */
  private static synchronized Stack.App changedApp() {
    if (changedApp == null) {
      changedApp =
          Stack.startApp(
              Map.of(
                  "OPTIONS_FILE",
                  Stack.resource("/acceptance/options-changed.json").toString(),
                  "JSON_COPY_DIR",
                  Stack.app().jsonCopyDir().toString()));
    }
    return changedApp;
  }

  @AfterAll
  static void stopChangedApp() {
    if (changedApp != null) {
      changedApp.close();
      changedApp = null;
    }
  }

  /** The offered options as "id|name|category", in the order of the answer. */
  private static List<String> offered(Stack.App app) {
    Api.Reply reply = Api.get(app, "/api/options");
    assertThat(reply.status()).as("status of %s", reply.text()).isEqualTo(200);
    List<String> options = new ArrayList<>();
    for (JsonNode option : reply.json().path("options")) {
      options.add(
          option.path("id").asString()
              + "|"
              + option.path("name").asString()
              + "|"
              + option.path("category").asString());
    }
    return options;
  }

  @Test
  void ac_003_01_activeOptionsAreOfferedWithNameAndCategory() {
    assertThat(offered(Stack.app()))
        .containsExactly(
            "ws-testing|Delavnica: testiranje programske opreme|workshop",
            "ws-security|Delavnica: varnost spletnih aplikacij|workshop",
            "ev-opening|Otvoritvena slovesnost|event",
            "ev-dinner|Slavnostna večerja|event",
            "meal-lunch-day1|Kosilo, prvi dan|meal",
            "meal-vegetarian|Vegetarijanski meni|meal",
            "other-city-tour|Voden ogled mesta|other");
  }

  @Test
  void ac_003_02_inactiveOptionIsNotOffered() {
    assertThat(offered(Stack.app()))
        .isNotEmpty()
        .noneMatch(option -> option.startsWith("ws-legacy|"));
  }

  @Test
  void ac_003_03_changedConfigurationChangesTheOfferedOptions() {
    assertThat(offered(changedApp()))
        .containsExactly(
            "ws-testing|Delavnica: testiranje programske opreme|workshop",
            "ws-new|Delavnica: umetna inteligenca|workshop",
            "ev-opening|Otvoritev v novi dvorani|event",
            "ev-dinner|Slavnostna večerja|event",
            "meal-lunch-day1|Kosilo, prvi dan|meal",
            "meal-vegetarian|Vegetarijanski meni|meal");
  }

  @Test
  void ac_003_03_fixedParticipantFieldsAreUnchangedByConfiguration() {
    UUID externalId =
        assertAccepted(
            Api.register(changedApp(), with(external(), "optionIds", List.of("ws-new"))));
    UUID studentId =
        assertAccepted(
            Api.register(changedApp(), with(student(), "optionIds", List.of("ev-opening"))));

    Map<String, Object> externalRow = registrationRow(externalId);
    assertThat(externalRow.get("organization")).isEqualTo("Podjetje Primer");
    assertThat(optionRows(externalId))
        .extracting(row -> row.get("option_name"))
        .containsExactly("Delavnica: umetna inteligenca");
    Map<String, Object> studentRow = registrationRow(studentId);
    assertThat(studentRow.get("study_institution")).isEqualTo("Univerza v Ljubljani");
    assertThat(studentRow.get("study_programme")).isEqualTo("Računalništvo in informatika");
    assertThat(studentRow.get("student_id")).isEqualTo("63210001");
    assertThat(optionRows(studentId))
        .extracting(row -> row.get("option_name"))
        .containsExactly("Otvoritev v novi dvorani");
  }

  @Test
  void ac_003_04_optionDeactivatedInConfigurationIsRejected() {
    assertAccepted(
        Api.register(Stack.app(), with(external(), "optionIds", List.of("ws-security"))));
    Stack.reset();

    Api.Reply reply =
        Api.register(changedApp(), with(external(), "optionIds", List.of("ws-security")));

    assertRejected(reply, "optionIds", "option_not_selectable");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_003_05_categoryWithoutActiveOptionDoesNotBlockRegistration() {
    assertThat(offered(changedApp())).isNotEmpty().noneMatch(option -> option.endsWith("|other"));

    UUID id =
        assertAccepted(
            Api.register(
                changedApp(), with(external(), "optionIds", List.of("ws-testing", "ev-dinner"))));

    assertThat(optionRows(id))
        .extracting(row -> row.get("option_category"))
        .containsExactly("workshop", "event");
  }
}
