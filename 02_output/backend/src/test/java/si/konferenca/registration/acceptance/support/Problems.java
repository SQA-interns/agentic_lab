package si.konferenca.registration.acceptance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Assertions on problem-details responses (api.openapi.yaml, Problem and FieldError). */
public final class Problems {

  /** Words that would reveal internals in a response shown to users (ES-07). */
  private static final List<String> INTERNALS =
      List.of(
          "exception",
          "sql",
          "postgres",
          "jdbc",
          "hibernate",
          "trigger",
          "stack",
          "org.",
          "java.",
          "injected");

  private Problems() {}

  /** Asserts the status and that the field errors contain the given field and code. */
  public static void assertFieldError(
      Api.Response response, int status, String field, String code) {
    assertThat(response.status()).as("status, body: %s", response.text()).isEqualTo(status);
    assertThat(response.contentType()).startsWith("application/problem+json");
    assertThat(fieldErrors(response.json()))
        .as("field errors of %s", response.text())
        .contains(field + ":" + code);
  }

  /** The field errors as "field:code". */
  public static List<String> fieldErrors(JsonNode problem) {
    List<String> result = new ArrayList<>();
    for (JsonNode error : problem.path("errors")) {
      result.add(error.path("field").asString() + ":" + error.path("code").asString());
    }
    return result;
  }

  /** Asserts a problem response that names nothing internal. */
  public static void assertNoInternals(Api.Response response) {
    assertThat(response.contentType()).startsWith("application/problem+json");
    String body = response.text().toLowerCase(java.util.Locale.ROOT);
    for (String word : INTERNALS) {
      assertThat(body).as("response body must not reveal internals").doesNotContain(word);
    }
  }
}
