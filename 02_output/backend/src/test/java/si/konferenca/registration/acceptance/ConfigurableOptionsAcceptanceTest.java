package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.student;
import static si.konferenca.registration.acceptance.support.Registrations.with;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.RunningApp.Response;

/** US-003 Configurable conference options: configuration file plus restart, no code change. */
class ConfigurableOptionsAcceptanceTest {

  private static final String HEADER =
      """
      conference:
        consent:
          id: data-processing
          text: "I agree to the processing of my personal data for organising the conference."
        options:
      """;

  @TempDir Path dir;

  private Map<String, String> configWithOptions(String optionLines) throws Exception {
    Path file = dir.resolve("options-" + System.nanoTime() + ".yaml");
    Files.writeString(file, HEADER + optionLines, StandardCharsets.UTF_8);
    return RunningApp.withValue(
        AcceptanceEnvironment.defaultConfiguration(), "OPTIONS_FILE", "file:" + file);
  }

  private static List<String> offeredIds(RunningApp app) {
    return app.get("/api/options")
        .json()
        .path("options")
        .valueStream()
        .map(o -> o.path("id").asString())
        .toList();
  }

  private static String stackText(Throwable t) {
    StringWriter w = new StringWriter();
    t.printStackTrace(new PrintWriter(w));
    return w.toString();
  }

  @Test
  void AC_003_01_optionAddedToConfigurationIsOfferedAfterRestart() throws Exception {
    Map<String, String> before =
        configWithOptions(
            "    - { id: ws-ai, name: \"AI workshop\", category: WORKSHOP, active: true }\n");
    try (RunningApp app = RunningApp.start(before)) {
      assertThat(offeredIds(app)).containsExactly("ws-ai");
    }
    Path file = Path.of(before.get("OPTIONS_FILE").substring("file:".length()));
    Files.writeString(
        file,
        HEADER
            + "    - { id: ws-ai, name: \"AI workshop\", category: WORKSHOP, active: true }\n"
            + "    - { id: ev-concert, name: \"Koncert v Križankah\", category: EVENT, active: true }\n",
        StandardCharsets.UTF_8);

    try (RunningApp app = RunningApp.start(before)) {
      assertThat(offeredIds(app)).containsExactlyInAnyOrder("ws-ai", "ev-concert");
      Map<String, Object> reg = with(external(), "optionIds", List.of("ev-concert"));
      Response r = app.register(reg);
      assertThat(r.status()).isEqualTo(201);
      Map<String, Object> row = app.registrationRows((String) reg.get("email")).get(0);
      Map<String, Object> option = app.optionRows(row.get("id")).get(0);
      assertThat(option.get("option_name")).isEqualTo("Koncert v Križankah");
      assertThat(option.get("option_category")).isEqualTo("EVENT");
    }
  }

  @Test
  void AC_003_02_optionMarkedInactiveIsNoLongerOfferedAndIsRejected() throws Exception {
    Map<String, String> config =
        configWithOptions(
            "    - { id: ws-ai, name: \"AI workshop\", category: WORKSHOP, active: true }\n"
                + "    - { id: meal-lunch, name: \"Lunch\", category: MEAL, active: true }\n");
    try (RunningApp app = RunningApp.start(config)) {
      assertThat(offeredIds(app)).contains("meal-lunch");
    }
    Path file = Path.of(config.get("OPTIONS_FILE").substring("file:".length()));
    Files.writeString(
        file,
        HEADER
            + "    - { id: ws-ai, name: \"AI workshop\", category: WORKSHOP, active: true }\n"
            + "    - { id: meal-lunch, name: \"Lunch\", category: MEAL, active: false }\n",
        StandardCharsets.UTF_8);

    try (RunningApp app = RunningApp.start(config)) {
      assertThat(offeredIds(app)).containsExactly("ws-ai");
      Map<String, Object> reg = with(external(), "optionIds", List.of("meal-lunch"));
      Response r = app.register(reg);
      assertThat(r.status()).isEqualTo(400);
      assertThat(r.fieldErrors()).containsValue("INACTIVE_OPTION");
      assertThat(app.registrationRows((String) reg.get("email"))).isEmpty();
    }
  }

  @Test
  void AC_003_03_changedOptionsDoNotChangeParticipantFields() throws Exception {
    Map<String, String> config =
        configWithOptions(
            "    - { id: other-yoga, name: \"Morning yoga\", category: OTHER, active: true }\n");
    try (RunningApp app = RunningApp.start(config)) {
      Map<String, Object> ext = with(external(), "optionIds", List.of("other-yoga"));
      Map<String, Object> stu = with(student(), "optionIds", List.of("other-yoga"));

      assertThat(app.register(ext).status()).isEqualTo(201);
      assertThat(app.register(stu).status()).isEqualTo(201);

      Response missingOrg = app.register(with(external(), "organization", ""));
      assertThat(missingOrg.fieldErrors()).containsEntry("organization", "REQUIRED");
      Response missingStudentId = app.register(with(student(), "studentId", ""));
      assertThat(missingStudentId.fieldErrors()).containsEntry("studentId", "REQUIRED");
    }
  }

  @Test
  void AC_003_04_unknownCategoryStopsStartupWithConfigurationError() throws Exception {
    Throwable t =
        RunningApp.startupFailure(
            configWithOptions(
                "    - { id: ws-ai, name: \"AI workshop\", category: BANQUET, active: true }\n"));

    assertThat(t).isNotNull();
    assertThat(stackText(t)).contains("BANQUET");
  }

  @Test
  void AC_003_04_duplicateIdentifierStopsStartupWithConfigurationError() throws Exception {
    Throwable t =
        RunningApp.startupFailure(
            configWithOptions(
                "    - { id: ws-dup, name: \"One\", category: WORKSHOP, active: true }\n"
                    + "    - { id: ws-dup, name: \"Two\", category: EVENT, active: true }\n"));

    assertThat(t).isNotNull();
    assertThat(stackText(t)).contains("ws-dup");
  }

  @Test
  void AC_003_04_missingDisplayNameStopsStartupWithConfigurationError() throws Exception {
    Throwable t =
        RunningApp.startupFailure(
            configWithOptions("    - { id: ws-noname, category: WORKSHOP, active: true }\n"));

    assertThat(t).isNotNull();
    assertThat(stackText(t)).contains("name");
  }
}
