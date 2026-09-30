package lab.conference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lab.conference.acceptance.support.Api;
import lab.conference.acceptance.support.AppInstance;
import lab.conference.acceptance.support.Catalogs;
import lab.conference.acceptance.support.Infra;
import lab.conference.acceptance.support.Payloads;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** US-003: activity catalog loaded from external configuration at startup. */
class CatalogAcceptanceTest {

  private static Map<String, Map<String, String>> activeOptions(JsonNode catalog) {
    Map<String, Map<String, String>> groups = new LinkedHashMap<>();
    for (JsonNode g : catalog.path("groups")) {
      Map<String, String> options = new LinkedHashMap<>();
      for (JsonNode o : g.path("options")) {
        options.put(o.path("id").asText(), o.path("name").asText());
      }
      groups.put(g.path("id").asText(), options);
    }
    return groups;
  }

  @Test
  void ac_003_01_catalogOffersExactlyTheActiveOptionsPerGroup() {
    try (AppInstance app = AppInstance.builder().build().start()) {
      Api.Response r = app.api().getCatalog();

      assertThat(r.status()).as(r.toString()).isEqualTo(200);
      JsonNode catalog = r.json();
      assertThat(catalog.path("conferenceTitle").asText()).isEqualTo("Lab Conference");
      Map<String, Map<String, String>> groups = activeOptions(catalog);
      assertThat(new ArrayList<>(groups.keySet()))
          .containsExactly("workshops", "events", "meals", "other");
      assertThat(groups.get("workshops"))
          .containsExactly(
              Map.entry("ws-alpha", "Workshop Alpha – Čebelarstvo"),
              Map.entry("ws-beta", "Workshop Beta"));
      assertThat(groups.get("events")).containsOnlyKeys("ev-gala");
      assertThat(groups.get("meals")).containsOnlyKeys("meal-veg");
      assertThat(groups.get("other")).containsOnlyKeys("oth-poster");
      assertThat(r.text()).doesNotContain("ws-inactive", "ev-off", "meal-off", "oth-off");
      assertThat(catalog.path("consent").path("required").asBoolean()).isTrue();
      assertThat(catalog.path("captcha").path("mode").asText()).isEqualTo("stub");
    }
  }

  @Test
  void ac_003_02_editedCatalogIsUsedAfterRestartWithoutRebuild() {
    Path catalogFile = Catalogs.path("catalog-v1.json");
    Infra.DatabaseRef db = Infra.newDatabase();
    AppInstance.Builder builder = AppInstance.builder().catalog(catalogFile).database(db);
    AppInstance first = builder.build().start();
    Path jsonDir = first.jsonDir();
    Map<String, Object> betaSelection = Payloads.external();
    betaSelection.put(
        "selections", Payloads.selections(List.of("ws-beta"), List.of(), List.of(), List.of()));
    assertThat(first.api().postExternal(betaSelection).status()).isEqualTo(201);
    first.stop();

    Catalogs.replace(catalogFile, "catalog-v2.json");
    try (AppInstance second =
        AppInstance.builder().catalog(catalogFile).database(db).jsonDir(jsonDir).build().start()) {
      Map<String, Map<String, String>> groups = activeOptions(second.api().getCatalog().json());
      assertThat(groups.get("workshops"))
          .containsExactly(
              Map.entry("ws-alpha", "Workshop Alpha (renamed)"),
              Map.entry("ws-gamma", "Workshop Gamma – new"));
      assertThat(groups.get("meals")).containsOnlyKeys("meal-veg", "meal-fish");

      Map<String, Object> added = Payloads.external();
      added.put(
          "selections",
          Payloads.selections(List.of("ws-gamma"), List.of(), List.of("meal-fish"), List.of()));
      assertThat(second.api().postExternal(added).status()).isEqualTo(201);

      Map<String, Object> deactivated = Payloads.fresh(betaSelection);
      Api.Response rejected = second.api().postExternal(deactivated);
      assertThat(rejected.status()).as(rejected.toString()).isEqualTo(400);
      assertThat(rejected.json().path("errors").findValuesAsText("field"))
          .anyMatch(f -> f.startsWith("selections"));
    }
  }

  @ParameterizedTest(name = "AC-003-03 {0}")
  @ValueSource(
      strings = {
        "catalog-invalid-malformed.json",
        "catalog-invalid-duplicate.json",
        "catalog-invalid-unknown-group.json",
        "catalog-invalid-missing-name.json"
      })
  void ac_003_03_invalidCatalogPreventsStartup(String file) {
    AppInstance app = AppInstance.builder().catalog(Catalogs.path(file)).build();
    try {
      assertThatThrownBy(app::start).as("backend must refuse to start with " + file).isNotNull();
    } finally {
      app.stop();
    }
  }

  @Test
  void ac_003_03_missingCatalogFilePreventsStartup() {
    AppInstance app = AppInstance.builder().catalog(Path.of("/nonexistent/catalog.json")).build();
    try {
      assertThatThrownBy(app::start).isNotNull();
    } finally {
      app.stop();
    }
  }

  @Test
  void ac_003_04_fixedFormFieldsAreUnchangedByCatalogChange() {
    try (AppInstance app =
        AppInstance.builder().catalog(Catalogs.path("catalog-v2.json")).build().start()) {
      Map<String, Object> external = Payloads.external();
      external.remove("organization");
      assertThat(app.api().postExternal(external).hasFieldError("organization", "REQUIRED"))
          .isTrue();

      Map<String, Object> student = Payloads.student();
      student.remove("studentId");
      assertThat(app.api().postStudent(student).hasFieldError("studentId", "REQUIRED")).isTrue();

      Map<String, Object> validStudent = Payloads.student();
      validStudent.put(
          "selections", Payloads.selections(List.of("ws-alpha"), List.of(), List.of(), List.of()));
      assertThat(app.api().postStudent(validStudent).status()).isEqualTo(201);
    }
  }
}
