package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Clients;
import si.konferenca.registration.acceptance.support.CustomContextTest;
import si.konferenca.registration.acceptance.support.TestEnvironment;

/** US-008 / SR-03: the export endpoint is rate limited per client. */
class ExportRateLimitAcceptanceTest extends CustomContextTest {

  private static final int LIMIT = 3;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    Map<String, String> p = TestEnvironment.baseProperties();
    p.put("app.rate-limit.export-per-minute", String.valueOf(LIMIT));
    AcceptanceTest.register(registry, p);
  }

  @Test
  @DisplayName("AC-008-04 export requests above the per-minute limit get 429")
  void ac008_04_limitsExportsPerClient() {
    String client = Clients.next();
    List<Integer> statuses = new ArrayList<>();
    for (int i = 0; i < LIMIT + 2; i++) {
      statuses.add(
          api.export(
                  TestEnvironment.ORGANIZER_USERNAME,
                  TestEnvironment.ORGANIZER_PASSWORD,
                  "X-Forwarded-For",
                  client)
              .status());
    }

    assertThat(statuses).containsExactly(200, 200, 200, 429, 429);
  }

  @Test
  @DisplayName("AC-008-04 failed logins count towards the export limit")
  void ac008_04_failedLoginsAreLimited() {
    String client = Clients.next();
    List<Integer> statuses = new ArrayList<>();
    for (int i = 0; i < LIMIT + 1; i++) {
      statuses.add(
          api.export(TestEnvironment.ORGANIZER_USERNAME, "guess-" + i, "X-Forwarded-For", client)
              .status());
    }

    assertThat(statuses).containsExactly(401, 401, 401, 429);
  }
}
