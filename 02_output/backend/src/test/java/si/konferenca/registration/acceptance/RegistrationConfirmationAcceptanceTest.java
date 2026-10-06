package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.with;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.RunningApp.Response;

/**
 * US-004 Registration confirmation, API side: the response the frontend bases the confirmation on.
 * The visible confirmation is covered by the frontend acceptance and end-to-end tests.
 */
class RegistrationConfirmationAcceptanceTest {

  private static RunningApp app;

  @BeforeAll
  static void start() {
    app = RunningApp.start();
  }

  @AfterAll
  static void stop() {
    app.close();
  }

  private static void assertNoInternals(Response r) {
    assertThat(r.text())
        .doesNotContainIgnoringCase("exception")
        .doesNotContain("at si.konferenca")
        .doesNotContain("org.springframework")
        .doesNotContain("tools.jackson")
        .doesNotContain("trace");
  }

  @Test
  void AC_004_01_acceptedRegistrationReturnsConfirmationData() {
    Instant before = Instant.now().minus(5, ChronoUnit.SECONDS);

    Response r = app.register(external());

    assertThat(r.status()).isEqualTo(201);
    assertThat(r.header("Content-Type")).startsWith("application/json");
    Instant receivedAt = Instant.parse(r.json().path("receivedAt").asString());
    assertThat(receivedAt).isAfter(before).isBefore(Instant.now().plusSeconds(5));
    assertThat(r.json().path("registrationId").asString()).isNotBlank();
  }

  @Test
  void AC_004_02_rejectedRegistrationReturnsFieldErrorsAndNoConfirmation() {
    Map<String, Object> reg = with(with(external(), "firstName", ""), "email", "wrong");

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.header("Content-Type")).startsWith("application/problem+json");
    assertThat(r.json().path("code").asString()).isEqualTo("VALIDATION_FAILED");
    assertThat(r.json().has("registrationId")).isFalse();
    assertThat(r.fieldErrors())
        .containsEntry("firstName", "REQUIRED")
        .containsEntry("email", "INVALID_EMAIL");
  }

  @Test
  void AC_004_03_malformedRequestGivesGeneralErrorWithoutInternals() {
    Response r = app.postJson("/api/registrations", "{\"type\": \"EXTERNAL\", \"firstName\": ");

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.header("Content-Type")).startsWith("application/problem+json");
    assertThat(r.json().has("registrationId")).isFalse();
    assertNoInternals(r);
  }

  @Test
  void AC_004_03_unknownPropertyGivesGeneralErrorWithoutInternals() {
    Response r = app.register(with(external(), "isAdmin", true));

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.json().has("registrationId")).isFalse();
    assertNoInternals(r);
  }

  @Test
  void AC_004_03_oversizedRequestIsRefusedWithoutInternals() {
    Response r = app.register(with(external(), "recaptchaToken", "x".repeat(40_000)));

    assertThat(r.status()).isEqualTo(413);
    assertThat(r.json().has("registrationId")).isFalse();
    assertNoInternals(r);
  }
}
