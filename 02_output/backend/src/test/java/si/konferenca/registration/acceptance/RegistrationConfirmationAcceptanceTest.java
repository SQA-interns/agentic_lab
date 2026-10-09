package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;

import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.ApiClient;
import tools.jackson.databind.node.ObjectNode;

/**
 * US-004 Registration confirmation: the API answers success only for a stored registration.
 * AC-004-03 is in {@link RegistrationStorageAcceptanceTest}; the confirmation shown in the
 * application is tested in the frontend acceptance and end-to-end tests.
 */
class RegistrationConfirmationAcceptanceTest extends AcceptanceTest {

  @Test
  void ac_004_01_successIsAnsweredOnlyOnceTheRegistrationIsStored() {
    ObjectNode request = external();

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().path("registrationId").asString();
    assertThat(db.registrationByEmail(request.path("email").asString())).isPresent();
    assertThat(copies.fileNames()).containsExactly("registration-" + id + ".json");
    assertThat(response.json().path("registeredAt").asString()).isNotBlank();
  }

  @Test
  void ac_004_02_rejectedRegistrationGetsFieldErrorsAndNoConfirmation() {
    ObjectNode request = external();
    request.put("firstName", "");
    request.put("email", "wrong");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(400);
    assertThat(response.json().has("registrationId")).isFalse();
    assertThat(fieldErrors(response.json())).contains("firstName:required", "email:invalid_email");
    for (var error : response.json().path("fieldErrors")) {
      assertThat(error.path("message").asString()).isNotBlank();
    }
    assertNothingStoredOrSent();
  }
}
