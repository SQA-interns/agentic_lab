package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Rate limiting per specification §8.3 and the 429 response in the contract. */
class RateLimitAcceptanceTest extends AcceptanceHarness {

  @DynamicPropertySource
  static void lowLimits(DynamicPropertyRegistry registry) {
    registerProperties(
        registry,
        Map.<String, Supplier<Object>>of(
            "app.rate-limit.registration-per-minute", () -> "3",
            "app.rate-limit.organizer-per-minute", () -> "3"));
  }

  @Autowired private Environment environment;

  @Test
  void harnessAppliesTheLowLimits() {
    assertThat(environment.getProperty("app.rate-limit.registration-per-minute")).isEqualTo("3");
  }

  @Test
  void registrationsBeyondTheLimitAreRejectedWith429() {
    int created = 0;
    Resp last = null;
    for (int i = 0; i < 4; i++) {
      last = register(validExternal(uniqueEmail("ratelimit")));
      if (last.status() == 201) {
        created++;
      }
    }

    assertThat(created).isEqualTo(3);
    assertThat(last.status()).isEqualTo(429);
    assertThat(last.header("Retry-After")).matches("\\d+");
    assertThat(last.json().path("code").asText()).isEqualTo("RATE_LIMITED");
  }

  @Test
  void organizerEndpointsAreRateLimited() {
    Resp last = null;
    for (int i = 0; i < 4; i++) {
      last = getAsOrganizer("/api/organizer/registrations/export", ORGANIZER_USER, "guess-" + i);
    }

    assertThat(last.status()).isEqualTo(429);
    assertThat(last.header("Retry-After")).matches("\\d+");
  }
}
