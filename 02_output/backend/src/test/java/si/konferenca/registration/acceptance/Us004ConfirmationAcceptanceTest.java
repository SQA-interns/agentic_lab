package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.with;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Json;
import tools.jackson.databind.JsonNode;

/**
 * US-004 Registration confirmation, API side: the confirmation data is returned only for an
 * accepted registration (BR-06). The rendering is covered by the frontend acceptance tests.
 */
class Us004ConfirmationAcceptanceTest extends AcceptanceTestBase {

  @Test
  void AC_004_01_accepted_registration_returns_the_confirmation_data() {
    Map<String, Object> body = external();

    Api.Response response = api.register(body);

    JsonNode accepted = assertAccepted(response);
    assertThat(accepted.path("id").asString()).isNotBlank();
    assertThat(accepted.path("registeredAt").asString()).isNotBlank();
    assertThat(accepted.path("email").asString()).isEqualTo(body.get("email"));
    assertThat(Json.strings(accepted.path("options"), "displayName"))
        .containsExactlyInAnyOrder("Workshop: AI in research", "Lunch, day 1");
    assertThat(db.countRegistrations()).isEqualTo(1);
  }

  @Test
  void AC_004_02_rejected_registration_returns_errors_and_no_confirmation_data() {
    Api.Response response = api.register(with(external(), "email", "not-an-email"));

    assertFieldError(response, "email", "INVALID_FORMAT");
    JsonNode body = response.json();
    assertThat(body.has("id")).isFalse();
    assertThat(body.has("registeredAt")).isFalse();
    assertNothingStored();
  }

  @Test
  void AC_004_03_registration_that_cannot_be_stored_returns_a_general_error_without_details() {
    copies.setWritable(false);

    Api.Response response = api.register(external());

    assertError(response, 503, "STORAGE_FAILED");
    String message = response.json().path("message").asString();
    assertThat(message).isNotBlank();
    for (String internal :
        List.of("Exception", "java.", "SQL", "/", "\\", "json-copies", "at si.", "Caused by")) {
      assertThat(response.text()).doesNotContain(internal);
    }
    assertThat(response.json().has("id")).isFalse();
    copies.setWritable(true);
    assertNothingStored();
  }
}
