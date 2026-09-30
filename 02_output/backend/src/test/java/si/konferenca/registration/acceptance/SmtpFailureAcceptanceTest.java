package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.CustomContextTest;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.JsonCopies;
import si.konferenca.registration.acceptance.support.TestEnvironment;

/** US-006 / D-08: an unreachable SMTP server does not lose or refuse the registration. */
class SmtpFailureAcceptanceTest extends CustomContextTest {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    Map<String, String> p = TestEnvironment.baseProperties();
    p.put("spring.mail.host", "127.0.0.1");
    p.put("spring.mail.port", "1");
    AcceptanceTest.register(registry, p);
  }

  @Test
  @DisplayName("AC-006-03 with SMTP unreachable the registration is still accepted and stored")
  void ac006_03_emailFailureKeepsRegistration() {
    Map<String, Object> payload = external();
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().get("id").asString();
    assertThat(Database.countByEmail(email)).isEqualTo(1);
    assertThat(JsonCopies.copyOf(TestEnvironment.defaultJsonDir(), id)).exists();
  }
}
