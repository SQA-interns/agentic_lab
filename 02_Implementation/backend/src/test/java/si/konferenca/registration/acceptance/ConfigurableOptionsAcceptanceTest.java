package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** US-003 — Configurable conference options (options file, specification §4). */
class ConfigurableOptionsAcceptanceTest extends AcceptanceTestBase {

  private Map<String, List<String>> idsByCategory() {
    Resp r = get("/api/options");
    assertThat(r.status()).as(r.text()).isEqualTo(200);
    Map<String, List<String>> out = new LinkedHashMap<>();
    for (JsonNode o : r.json()) {
      out.computeIfAbsent(o.path("category").asText(), k -> new ArrayList<>())
          .add(o.path("id").asText());
    }
    return out;
  }

  @Test
  void ac_003_01_optionsAreGroupedByConfigurableSet() {
    Map<String, List<String>> byCategory = idsByCategory();

    assertThat(byCategory.keySet()).containsExactly("WORKSHOP", "EVENT", "MEAL", "OTHER");
    assertThat(byCategory.get("WORKSHOP")).containsExactly("ws-ai", "ws-sec");
    assertThat(byCategory.get("EVENT")).containsExactly("ev-dinner");
    assertThat(byCategory.get("MEAL")).containsExactly("meal-lunch1");
    assertThat(byCategory.get("OTHER")).containsExactly("other-tour");
  }

  @Test
  void ac_003_02_newlyConfiguredOptionIsOfferedAndSelectable() {
    writeOptionsFile(
        withChanged(defaultOptions(), new Opt("ws-new", "WORKSHOP", "Nova delavnica", true)));

    Resp options = get("/api/options");
    assertThat(options.text()).contains("ws-new").contains("Nova delavnica");

    String email = uniqueEmail("ac00302");
    Resp r = register(validExternal(email, "ws-new"));
    assertThat(r.status()).as(r.text()).isEqualTo(201);
    assertThat(optionIdsOf(r.json().path("registrationId").asText())).containsExactly("ws-new");
  }

  @Test
  void ac_003_03_deactivatedOptionStopsBeingOfferedAndAccepted() {
    writeOptionsFile(
        withChanged(defaultOptions(), new Opt("ev-dinner", "EVENT", "Conference dinner", false)));

    assertThat(idsByCategory().getOrDefault("EVENT", List.of())).doesNotContain("ev-dinner");

    String email = uniqueEmail("ac00303");
    assertRejectedWithFieldError(
        register(validExternal(email, "ev-dinner")), "optionIds[0]", "INACTIVE_OPTION");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @Test
  void ac_003_03_optionRemovedFromFileStopsBeingOffered() {
    List<Opt> without = new ArrayList<>(defaultOptions());
    without.removeIf(o -> o.id().equals("other-tour"));
    writeOptionsFile(without);

    assertThat(idsByCategory().getOrDefault("OTHER", List.of())).doesNotContain("other-tour");
    String email = uniqueEmail("ac00303b");
    Resp r = register(validExternal(email, "other-tour"));
    assertThat(r.status()).isEqualTo(400);
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @Test
  void ac_003_04_existingRegistrationsKeepTheirSelectionsAfterDeactivation() {
    String email = uniqueEmail("ac00304");
    Resp r = register(validExternal(email, "ws-sec"));
    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String id = r.json().path("registrationId").asText();

    writeOptionsFile(
        withChanged(
            defaultOptions(), new Opt("ws-sec", "WORKSHOP", "Varnost spletnih aplikacij", false)));
    assertThat(get("/api/options").text()).doesNotContain("ws-sec");

    assertThat(optionIdsOf(id)).containsExactly("ws-sec");
    List<List<String>> rows = ExcelReader.rows(exportAsOrganizer().body());
    List<String> row = rows.stream().filter(x -> x.get(0).equals(id)).findFirst().orElseThrow();
    assertThat(row.get(10)).isEqualTo("Varnost spletnih aplikacij");
  }

  @Test
  void ac_003_05_renamingKeepsTheAssociationByStableIdentifier() {
    String email = uniqueEmail("ac00305");
    Resp r = register(validExternal(email, "meal-lunch1"));
    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String id = r.json().path("registrationId").asText();

    writeOptionsFile(
        withChanged(defaultOptions(), new Opt("meal-lunch1", "MEAL", "Kosilo (1. dan)", true)));

    assertThat(get("/api/options").text()).contains("Kosilo (1. dan)");
    assertThat(optionIdsOf(id)).containsExactly("meal-lunch1");
    List<String> row =
        ExcelReader.rows(exportAsOrganizer().body()).stream()
            .filter(x -> x.get(0).equals(id))
            .findFirst()
            .orElseThrow();
    assertThat(row.get(12)).isEqualTo("Kosilo (1. dan)");
  }
}
