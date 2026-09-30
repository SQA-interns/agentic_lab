package si.konferenca.registration.acceptance;

import static si.konferenca.registration.acceptance.support.Checks.assertFieldError;
import static si.konferenca.registration.acceptance.support.Checks.assertNothingStored;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.with;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.CustomContextTest;
import si.konferenca.registration.acceptance.support.RecaptchaMock;
import si.konferenca.registration.acceptance.support.TestEnvironment;

/** US-001 / SR-01: an unreachable verification endpoint fails closed. */
class RecaptchaUnreachableAcceptanceTest extends CustomContextTest {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    Map<String, String> p = TestEnvironment.baseProperties();
    p.put("app.recaptcha.test-mode", "false");
    p.put("app.recaptcha.site-key", RecaptchaMock.SITE_KEY);
    p.put("app.recaptcha.secret-key", RecaptchaMock.SECRET);
    p.put("app.recaptcha.verify-url", "http://127.0.0.1:1/siteverify");
    AcceptanceTest.register(registry, p);
  }

  @Test
  @DisplayName("AC-001-08 an unreachable verification endpoint gives 400 and stores nothing")
  void ac001_08_rejectsWhenEndpointUnreachable() {
    Map<String, Object> payload = with(external(), "recaptchaToken", RecaptchaMock.GOOD_TOKEN);
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertFieldError(response, 400, "recaptchaToken");
    assertNothingStored(email, TestEnvironment.defaultJsonDir());
  }
}
