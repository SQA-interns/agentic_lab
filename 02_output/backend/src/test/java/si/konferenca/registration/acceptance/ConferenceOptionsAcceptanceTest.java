package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Fixtures;
import tools.jackson.databind.JsonNode;

/** US-003 configurable conference options (active options, availability, limits). */
class ConferenceOptionsAcceptanceTest extends AcceptanceTestBase {

  @Test
  @DisplayName("AC-003-01 only active options are returned, with id, name and category")
  void ac00301OnlyActiveOptionsAreReturned() {
    Api.Response r = api.get("/api/options?type=EXTERNAL");

    assertThat(r.status()).as(r.text()).isEqualTo(200);
    JsonNode body = r.json();
    assertThat(ids(body.path("options")))
        .containsExactly(
            "ws-secure-web",
            "ws-data-science",
            "ev-welcome",
            "ev-gala-dinner",
            "meal-lunch-day1",
            "meal-lunch-day2",
            "meal-vegetarian",
            "other-city-tour");
    JsonNode first = body.path("options").get(0);
    assertThat(first.path("name").asString()).isEqualTo("Workshop: Secure web development");
    assertThat(first.path("category").asString()).isEqualTo("WORKSHOP");
    assertThat(categories(body.path("options")))
        .containsExactlyInAnyOrder("WORKSHOP", "EVENT", "MEAL", "OTHER");
    assertThat(ids(body.path("consents"))).containsExactly("data-processing", "photos");
    assertThat(body.path("consents").get(0).path("mandatory").asBoolean()).isTrue();
    assertThat(body.path("categoryLimits").path("MEAL").asInt()).isEqualTo(2);
  }

  @Test
  @DisplayName("AC-003-01 an unknown registration type is rejected")
  void ac00301UnknownTypeIsRejected() {
    Api.Response r = api.get("/api/options?type=VIP");

    assertThat(r.status()).isEqualTo(400);
    assertNoInternals(r);
  }

  @Test
  @DisplayName("AC-003-04 an option restricted to one type is offered only to that type")
  void ac00304RestrictedOptionIsOfferedOnlyToItsType() {
    List<String> external = ids(api.get("/api/options?type=EXTERNAL").json().path("options"));
    List<String> student = ids(api.get("/api/options?type=STUDENT").json().path("options"));

    assertThat(external).contains("ev-gala-dinner").doesNotContain("ev-career-fair");
    assertThat(student).contains("ev-career-fair").doesNotContain("ev-gala-dinner");
  }

  @Test
  @DisplayName("AC-003-04 a student selecting an external-only option is rejected")
  void ac00304StudentSelectingExternalOnlyOptionIsRejected() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.student(email);
    request.put("optionIds", List.of("ev-gala-dinner"));

    assertRejected(api.register(request), "optionIds");
    assertNothingStored(email);
  }

  @Test
  @DisplayName("AC-003-04 an external participant selecting a student-only option is rejected")
  void ac00304ExternalSelectingStudentOnlyOptionIsRejected() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("optionIds", List.of("ev-career-fair"));

    assertRejected(api.register(request), "optionIds");
    assertNothingStored(email);
  }

  @Test
  @DisplayName("AC-003-05 more options of a category than its configured maximum are rejected")
  void ac00305CategoryLimitIsEnforced() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("optionIds", List.of("meal-lunch-day1", "meal-lunch-day2", "meal-vegetarian"));

    assertRejected(api.register(request), "optionIds");
    assertNothingStored(email);
  }

  @Test
  @DisplayName("AC-003-05 selecting the configured maximum of a category is accepted")
  void ac00305CategoryLimitAllowsMaximum() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("optionIds", List.of("meal-lunch-day1", "meal-lunch-day2", "ws-data-science"));

    assertThat(api.register(request).status()).isEqualTo(201);
  }

  @Test
  @DisplayName("AC-003-05 selecting the same option twice is rejected")
  void ac00305DuplicateOptionIsRejected() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("optionIds", List.of("ws-secure-web", "ws-secure-web"));

    assertRejected(api.register(request), "optionIds");
    assertNothingStored(email);
  }

  static List<String> ids(JsonNode array) {
    return array.valueStream().map(o -> o.path("id").asString()).toList();
  }

  private static List<String> categories(JsonNode array) {
    return array.valueStream().map(o -> o.path("category").asString()).distinct().toList();
  }
}
