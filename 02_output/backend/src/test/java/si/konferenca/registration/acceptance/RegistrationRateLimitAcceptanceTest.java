package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Checks.assertNothingStored;
import static si.konferenca.registration.acceptance.support.Payloads.external;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Clients;
import si.konferenca.registration.acceptance.support.CustomContextTest;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.TestEnvironment;

/** US-001 / SR-03: registration requests are rate limited per client. */
class RegistrationRateLimitAcceptanceTest extends CustomContextTest {

  private static final int LIMIT = 3;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    Map<String, String> p = TestEnvironment.baseProperties();
    p.put("app.rate-limit.registration-per-minute", String.valueOf(LIMIT));
    AcceptanceTest.register(registry, p);
  }

  @Test
  @DisplayName("AC-001-10 registration requests above the per-minute limit get 429")
  void ac001_10_limitsRegistrationsPerClient() {
    String client = Clients.next();
    List<Integer> statuses = new ArrayList<>();
    List<String> emails = new ArrayList<>();
    for (int i = 0; i < LIMIT + 2; i++) {
      Map<String, Object> payload = external();
      emails.add((String) payload.get("email"));
      statuses.add(api.register(payload, client).status());
    }

    assertThat(statuses).containsExactly(201, 201, 201, 429, 429);
    for (int i = 0; i < LIMIT; i++) {
      assertThat(Database.countByEmail(emails.get(i))).isEqualTo(1);
    }
    assertNothingStored(emails.get(LIMIT), TestEnvironment.defaultJsonDir());
    assertNothingStored(emails.get(LIMIT + 1), TestEnvironment.defaultJsonDir());
  }

  @Test
  @DisplayName("AC-001-10 a rate-limited response has the error body and Retry-After")
  void ac001_10_rateLimitedResponseShape() {
    String client = Clients.next();
    Api.Response last = null;
    for (int i = 0; i < LIMIT + 1; i++) {
      last = api.register(external(), client);
    }

    assertThat(last.status()).isEqualTo(429);
    assertThat(last.json().path("error").asString()).isEqualTo("rate_limited");
    assertThat(last.header("Retry-After")).isNotBlank();
  }

  @Test
  @DisplayName("AC-001-10 the limit applies per client: another client is not affected")
  void ac001_10_otherClientsUnaffected() {
    String busy = Clients.next();
    for (int i = 0; i < LIMIT + 1; i++) {
      api.register(external(), busy);
    }

    Api.Response other = api.register(external(), Clients.next());

    assertThat(other.status()).as(other.text()).isEqualTo(201);
  }
}
