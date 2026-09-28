package org.example.conference.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Server-side reCAPTCHA verification with a mocked endpoint: never calls Google (ST-05). */
class RecaptchaVerifierTest {

  private static final String URL = "https://captcha.invalid/siteverify";
  private MockRestServiceServer server;
  private RecaptchaVerifier verifier;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    verifier = new RecaptchaVerifier(builder.build(), URL, "the-secret");
  }

  @Test
  void acceptsOnlyWhenGoogleConfirmsSuccess() {
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("secret=the-secret")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("response=tok")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("remoteip=10.0.0.1")))
        .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
    assertThat(verifier.verify("tok", "10.0.0.1")).isTrue();
    server.verify();
  }

  @Test
  void rejectsUnsuccessfulResponse() {
    server
        .expect(requestTo(URL))
        .andRespond(
            withSuccess(
                "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}",
                MediaType.APPLICATION_JSON));
    assertThat(verifier.verify("tok", null)).isFalse();
  }

  @Test
  void failsClosedOnError() {
    server.expect(requestTo(URL)).andRespond(withServerError());
    assertThat(verifier.verify("tok", null)).isFalse();
  }

  @Test
  void rejectsBlankOrOversizedTokenWithoutCallingEndpoint() {
    assertThat(verifier.verify(null, null)).isFalse();
    assertThat(verifier.verify(" ", null)).isFalse();
    assertThat(verifier.verify("x".repeat(5000), null)).isFalse();
    server.verify();
  }
}
