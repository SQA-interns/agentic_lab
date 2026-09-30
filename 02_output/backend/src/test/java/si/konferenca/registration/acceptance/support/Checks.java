package si.konferenca.registration.acceptance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Assertions shared by acceptance tests. */
public final class Checks {

  private Checks() {}

  /** Field names listed in an Error body (openapi.json, Error.fieldErrors). */
  public static List<String> errorFields(Api.Response response) {
    List<String> fields = new ArrayList<>();
    JsonNode errors = response.json().path("fieldErrors");
    errors.forEach(e -> fields.add(e.path("field").asString("")));
    return fields;
  }

  /** The response is an Error body with the status and a field error on the field. */
  public static void assertFieldError(Api.Response response, int status, String field) {
    assertThat(response.status()).as("status, body: %s", response.text()).isEqualTo(status);
    assertThat(response.json().path("status").asInt()).isEqualTo(status);
    assertThat(errorFields(response)).as("fieldErrors of %s", response.text()).contains(field);
  }

  /** No database row and no JSON copy exist for the email address. */
  public static void assertNothingStored(String email, Path jsonDir) {
    assertThat(Database.countByEmail(email)).as("database rows for %s", email).isZero();
    assertThat(JsonCopies.containing(jsonDir, email)).as("JSON copies for %s", email).isEmpty();
  }

  /** The error body reveals no internals (ES-07, SB-07). */
  public static void assertNoInternals(Api.Response response) {
    String body = response.text();
    assertThat(body)
        .doesNotContain("Exception")
        .doesNotContain("java.")
        .doesNotContain("at si.")
        .doesNotContain("org.springframework")
        .doesNotContain("SQL")
        .doesNotContain("trace");
  }
}
