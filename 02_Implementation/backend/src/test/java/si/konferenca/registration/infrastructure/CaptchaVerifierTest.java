package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.service.CaptchaVerifier;

class CaptchaVerifierTest {

  private static final String VERIFY_URL = "https://recaptcha.test/siteverify";

  private static AppProperties properties(String secret) {
    return new AppProperties(
        new AppProperties.Recaptcha(false, secret, "public-site-key", VERIFY_URL),
        new AppProperties.Mail("from@test", List.of()),
        new AppProperties.Backup("./build"),
        new AppProperties.Options(null),
        new AppProperties.Organizer("organizer", null),
        new AppProperties.RateLimit(
            new AppProperties.Limit(10, 600), new AppProperties.Limit(30, 600)),
        new AppProperties.Request(16384));
  }

  @Test
  void testModeAcceptsOnlyTheDeterministicToken() {
    TestModeCaptchaVerifier verifier = new TestModeCaptchaVerifier();

    assertThat(verifier.verify("test-mode-pass", null)).isTrue();
    assertThat(verifier.verify("anything-else", null)).isFalse();
    assertThat(verifier.verify(null, null)).isFalse();
    assertThat(verifier.settings().mode()).isEqualTo(CaptchaVerifier.CaptchaSettings.Mode.TEST);
    assertThat(verifier.settings().siteKey()).isNull();
  }

  @Test
  void productionModeRequiresSecret() {
    assertThatThrownBy(() -> new RecaptchaCaptchaVerifier(properties(" "), RestClient.builder()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RECAPTCHA_SECRET_KEY");
  }

  @Test
  void productionModeAcceptsSuccessfulGoogleVerification() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(VERIFY_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("secret=s3cret")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("response=token-1")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("remoteip=192.0.2.4")))
        .andRespond(
            withSuccess(
                "{\"success\":true,\"hostname\":\"x\",\"challenge_ts\":\"2026-01-01T00:00:00Z\"}",
                MediaType.APPLICATION_JSON));
    RecaptchaCaptchaVerifier verifier =
        new RecaptchaCaptchaVerifier(properties("s3cret"), builder.build());

    assertThat(verifier.verify("token-1", "192.0.2.4")).isTrue();
    assertThat(verifier.settings().mode())
        .isEqualTo(CaptchaVerifier.CaptchaSettings.Mode.RECAPTCHA);
    assertThat(verifier.settings().siteKey()).isEqualTo("public-site-key");
    server.verify();
  }

  @Test
  void productionModeRejectsFailedVerification() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(VERIFY_URL))
        .andRespond(
            withSuccess(
                "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}",
                MediaType.APPLICATION_JSON));
    RecaptchaCaptchaVerifier verifier =
        new RecaptchaCaptchaVerifier(properties("s3cret"), builder.build());

    assertThat(verifier.verify("forged", null)).isFalse();
  }

  @Test
  void productionModeRejectsWhenGoogleIsUnavailable() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo(VERIFY_URL)).andRespond(withServerError());
    RecaptchaCaptchaVerifier verifier =
        new RecaptchaCaptchaVerifier(properties("s3cret"), builder.build());

    assertThat(verifier.verify("token", null)).isFalse();
  }
}
