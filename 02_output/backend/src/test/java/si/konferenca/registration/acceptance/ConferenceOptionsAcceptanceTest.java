package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.options;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.AppInstance;
import si.konferenca.registration.acceptance.support.TestStack;
import tools.jackson.databind.JsonNode;

/** US-003 Configurable conference options, through the form data and registration API. */
class ConferenceOptionsAcceptanceTest extends AcceptanceTest {

  private static final String CONSENTS =
      "\"consents\": [{\"id\": \"data-processing\", \"text\": \"I agree.\", \"mandatory\": true}]";

  @Test
  void ac_003_01_formDataOffersActiveOptionsGroupedByCategoryWithDisplayNames() {
    ApiClient.Response response = api.getRegistrationForm();

    assertThat(response.status()).as(response.text()).isEqualTo(200);
    JsonNode form = response.json();
    assertThat(categories(form)).containsExactly("workshop", "event", "meal", "other");
    assertThat(optionNames(form, "workshop"))
        .containsExactly(
            "ws-ai=Delavnica umetne inteligence", "ws-security=Varnost spletnih aplikacij");
    assertThat(optionNames(form, "event"))
        .containsExactly("ev-reception=Welcome reception", "ev-industry-dinner=Industry dinner");
    assertThat(optionNames(form, "meal"))
        .containsExactly("meal-lunch-1=Lunch, day 1", "meal-lunch-2=Lunch, day 2");
    assertThat(optionNames(form, "other")).containsExactly("other-career-fair=Career fair");
    assertThat(category(form, "workshop").path("maxSelections").asInt()).isEqualTo(1);
    assertThat(category(form, "event").path("maxSelections").asInt()).isEqualTo(2);
    assertThat(form.path("conferenceName").asString()).isEqualTo(TestStack.CONFERENCE_NAME);
    assertThat(form.path("captcha").path("mode").asString()).isEqualTo("test");
    JsonNode consent = form.path("consents").get(0);
    assertThat(consent.path("id").asString()).isEqualTo("data-processing");
    assertThat(consent.path("mandatory").asBoolean()).isTrue();
  }

  @Test
  void ac_003_02_inactiveOptionIsNotOffered() {
    ApiClient.Response response = api.getRegistrationForm();

    assertThat(response.status()).as(response.text()).isEqualTo(200);
    assertThat(response.text()).doesNotContain("ws-legacy").doesNotContain("Retired workshop");
  }

  @Test
  void ac_003_03_optionAvailabilityPerRegistrationTypeIsPartOfTheFormData() {
    ApiClient.Response response = api.getRegistrationForm();

    assertThat(response.status()).as(response.text()).isEqualTo(200);
    JsonNode form = response.json();
    assertThat(availableTo(form, "event", "ev-industry-dinner")).containsExactly("EXTERNAL");
    assertThat(availableTo(form, "other", "other-career-fair")).containsExactly("STUDENT");
    assertThat(availableTo(form, "workshop", "ws-ai"))
        .containsExactlyInAnyOrder("EXTERNAL", "STUDENT");
  }

  @Test
  void ac_003_04_changedConfigurationIsFollowedAfterRestartWithoutCodeChange() throws IOException {
    Path file =
        optionsFile(
            "{\"categories\": {\"workshop\": {\"maxSelections\": 2}},"
                + " \"options\": ["
                + "{\"id\": \"ws-ai\", \"name\": \"Renamed AI workshop\", \"category\": \"workshop\","
                + " \"active\": true},"
                + "{\"id\": \"ws-security\", \"name\": \"Varnost\", \"category\": \"workshop\","
                + " \"active\": false},"
                + "{\"id\": \"ws-new\", \"name\": \"Brand new workshop\", \"category\": \"workshop\","
                + " \"active\": true}],"
                + CONSENTS
                + "}");

    try (AppInstance restarted = AppInstance.start(Map.of("OPTIONS_FILE", location(file)))) {
      ApiClient restartedApi = restarted.api();
      ApiClient.Response form = restartedApi.getRegistrationForm();
      assertThat(form.status()).as(form.text()).isEqualTo(200);
      assertThat(optionNames(form.json(), "workshop"))
          .containsExactly("ws-ai=Renamed AI workshop", "ws-new=Brand new workshop");
      assertThat(category(form.json(), "workshop").path("maxSelections").asInt()).isEqualTo(2);

      ApiClient.Response inactive = restartedApi.register(options(external(), "ws-security"));
      assertFieldError(inactive, "optionIds", "inactive_option");

      ApiClient.Response accepted = restartedApi.register(options(external(), "ws-ai", "ws-new"));
      assertThat(accepted.status()).as(accepted.text()).isEqualTo(201);
    }
  }

  @Test
  void ac_003_05_configurationCannotDefineParticipantFields() throws IOException {
    Path file =
        optionsFile(
            "{\"categories\": {}, \"options\": [],"
                + " \"fields\": [{\"name\": \"phone\", \"required\": true}],"
                + CONSENTS
                + "}");

    Throwable failure = catchThrowable(() -> startAndStop(file));

    assertThat(failure).as("startup with a field definition in the options file").isNotNull();
    assertThat(causeChain(failure)).contains("fields");
  }

  @ParameterizedTest(name = "AC-003-06 {0}")
  @CsvSource(
      value = {
        "duplicate option id|ws-twice|{\"id\": \"ws-twice\", \"name\": \"A\", \"category\": \"workshop\","
            + " \"active\": true}, {\"id\": \"ws-twice\", \"name\": \"B\", \"category\": \"workshop\","
            + " \"active\": true}",
        "unknown category|lecturez|{\"id\": \"x-1\", \"name\": \"A\", \"category\": \"lecturez\","
            + " \"active\": true}",
        "missing display name|name|{\"id\": \"x-2\", \"category\": \"meal\", \"active\": true}"
      },
      delimiter = '|')
  void ac_003_06_invalidConfigurationPreventsStartupAndIsReported(
      String description, String reported, String optionsJson) throws IOException {
    Path file =
        optionsFile("{\"categories\": {}, \"options\": [" + optionsJson + "]," + CONSENTS + "}");

    Throwable failure = catchThrowable(() -> startAndStop(file));

    assertThat(failure).as("startup with " + description).isNotNull();
    assertThat(causeChain(failure)).contains(reported);
  }

  private static String location(Path file) {
    return file.toUri().toString();
  }

  private static void startAndStop(Path optionsFile) {
    AppInstance.start(Map.of("OPTIONS_FILE", location(optionsFile))).close();
  }

  private static Path optionsFile(String json) throws IOException {
    Path file = Files.createTempFile("conference-options", ".json");
    Files.writeString(file, json, StandardCharsets.UTF_8);
    return file;
  }

  private static String causeChain(Throwable failure) {
    StringBuilder text = new StringBuilder();
    for (Throwable t = failure; t != null; t = t.getCause()) {
      text.append(t.getMessage()).append('\n');
    }
    return text.toString();
  }

  private static List<String> categories(JsonNode form) {
    List<String> names = new ArrayList<>();
    for (JsonNode category : form.path("categories")) {
      names.add(category.path("category").asString());
    }
    return names;
  }

  private static JsonNode category(JsonNode form, String name) {
    for (JsonNode category : form.path("categories")) {
      if (category.path("category").asString().equals(name)) {
        return category;
      }
    }
    throw new AssertionError("no category " + name + " in " + form);
  }

  private static List<String> optionNames(JsonNode form, String categoryName) {
    List<String> options = new ArrayList<>();
    for (JsonNode option : category(form, categoryName).path("options")) {
      options.add(option.path("id").asString() + "=" + option.path("name").asString());
    }
    return options;
  }

  private static List<String> availableTo(JsonNode form, String categoryName, String optionId) {
    for (JsonNode option : category(form, categoryName).path("options")) {
      if (option.path("id").asString().equals(optionId)) {
        List<String> types = new ArrayList<>();
        option.path("availableTo").forEach(type -> types.add(type.asString()));
        return types;
      }
    }
    throw new AssertionError("no option " + optionId);
  }
}
