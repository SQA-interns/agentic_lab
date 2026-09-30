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
import si.konferenca.registration.acceptance.support.TestEnvironment;

/** US-008 / SR-06: organizer credentials are refused over plain HTTP. */
class ExportHttpsOnlyAcceptanceTest extends CustomContextTest {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    Map<String, String> p = TestEnvironment.baseProperties();
    p.put("app.organizer.https-only", "true");
    AcceptanceTest.register(registry, p);
  }

  @Test
  @DisplayName("AC-008-03 valid credentials over plain HTTP are refused with 403")
  void ac008_03_refusesPlainHttp() {
    Map<String, Object> ext = external();
    assertThat(api.register(ext).status()).isEqualTo(201);

    Api.Response response =
        api.export(TestEnvironment.ORGANIZER_USERNAME, TestEnvironment.ORGANIZER_PASSWORD);

    assertThat(response.status()).isEqualTo(403);
    assertThat(response.json().path("error").asString()).isEqualTo("forbidden");
    assertThat(response.text()).doesNotContain((String) ext.get("email"));
  }

  @Test
  @DisplayName("AC-008-03 the same request forwarded by the proxy as HTTPS succeeds")
  void ac008_03_acceptsForwardedHttps() {
    Api.Response response =
        api.export(
            TestEnvironment.ORGANIZER_USERNAME,
            TestEnvironment.ORGANIZER_PASSWORD,
            "X-Forwarded-Proto",
            "https");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.header("Content-Type"))
        .startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
  }
}
