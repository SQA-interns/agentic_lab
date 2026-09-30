package lab.conference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import lab.conference.acceptance.support.Api;
import lab.conference.acceptance.support.AppInstance;
import lab.conference.acceptance.support.Payloads;
import org.junit.jupiter.api.Test;

/** AC-001-10 and AC-008-05: documented rate limits per client address. */
class RateLimitAcceptanceTest {

  @Test
  void ac_001_10_registrationsAboveTheRateLimitAreRejectedAndNotStored() {
    try (AppInstance app =
        AppInstance.builder().set("REGISTRATION_RATE_LIMIT_PER_MINUTE", "3").build().start()) {
      for (int i = 0; i < 3; i++) {
        Api.Response ok = app.api().postExternal(Payloads.external());
        assertThat(ok.status()).as(ok.toString()).isEqualTo(201);
      }
      Map<String, Object> excess = Payloads.student();

      Api.Response limited = app.api().postStudent(excess);

      assertThat(limited.status()).as(limited.toString()).isEqualTo(429);
      assertThat(limited.header("Retry-After")).isNotBlank();
      assertThat(app.store().registrationCount()).isEqualTo(3);
      assertThat(app.store().registrationsWithEmail((String) excess.get("email"))).isZero();
    }
  }

  @Test
  void ac_008_05_exportAuthenticationAttemptsAboveTheLimitAreRefused() {
    try (AppInstance app =
        AppInstance.builder().set("EXPORT_RATE_LIMIT_PER_MINUTE", "3").build().start()) {
      for (int i = 0; i < 3; i++) {
        Api.Response denied = app.api().export(app.organizerUser(), "guess-" + i);
        assertThat(denied.status()).as(denied.toString()).isEqualTo(401);
      }

      Api.Response limited = app.api().export(app.organizerUser(), app.organizerPassword());

      assertThat(limited.status()).as(limited.toString()).isEqualTo(429);
      assertThat(limited.header("Retry-After")).isNotBlank();
      assertThat(limited.header("Content-Type")).doesNotContain("spreadsheet");
    }
  }
}
