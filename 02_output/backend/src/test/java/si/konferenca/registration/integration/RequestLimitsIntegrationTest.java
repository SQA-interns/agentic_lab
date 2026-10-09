package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import si.konferenca.registration.RegistrationApplication;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Registrations;
import si.konferenca.registration.acceptance.support.TestStack;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Request limits and CORS details found by the targeted mutation run (SR-03). */
class RequestLimitsIntegrationTest extends AcceptanceTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private final HttpClient http = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  private HttpResponse<String> post(HttpRequest.BodyPublisher body) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/registrations"))
            .header("Content-Type", "application/json")
            .POST(body)
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void oversizedBodyWithoutContentLengthIsRefusedWhileRead() throws Exception {
    ObjectNode body = Registrations.external();
    body.put("organization", "x".repeat(20_000));
    byte[] bytes = JSON.writeValueAsBytes(body);

    HttpResponse<String> response =
        post(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes)));

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(response.body()).contains("payload_too_large");
    assertThat(db.countRegistrations()).isZero();
  }

  @Test
  void fiftyOptionIdsAndTwentyConsentIdsAreTheLargestAcceptedLists() throws Exception {
    ObjectNode atLimit = Registrations.external();
    ArrayNode options = atLimit.putArray("optionIds");
    for (int i = 0; i < 50; i++) {
      options.add("o" + i);
    }
    ArrayNode consents = atLimit.putArray("consentIds");
    consents.add(Registrations.CONSENT);
    for (int i = 1; i < 20; i++) {
      consents.add("c" + i);
    }
    ObjectNode tooManyConsents = atLimit.deepCopy();
    tooManyConsents.withArray("consentIds").add("c20");

    HttpResponse<String> limit =
        post(
            HttpRequest.BodyPublishers.ofString(
                JSON.writeValueAsString(atLimit), StandardCharsets.UTF_8));
    HttpResponse<String> over =
        post(
            HttpRequest.BodyPublishers.ofString(
                JSON.writeValueAsString(tooManyConsents), StandardCharsets.UTF_8));

    assertThat(limit.statusCode()).isEqualTo(400);
    assertThat(limit.body()).contains("validation_failed").contains("unknown_option");
    assertThat(over.statusCode()).isEqualTo(400);
    assertThat(over.body()).contains("invalid_request");
  }

  @Test
  void configuredOriginMayCallTheApiAndOthersMayNot() throws Exception {
    Map<String, Object> properties =
        new HashMap<>(
            TestStack.properties(Map.of("CORS_ALLOWED_ORIGINS", "http://dev.local:5173")));
    properties.put("server.port", "0");
    properties.put("JSON_COPY_DIR", TestStack.newTempDirectory("cors-copies").toString());
    try (ConfigurableApplicationContext app =
        new SpringApplicationBuilder(RegistrationApplication.class).properties(properties).run()) {
      String base = "http://127.0.0.1:" + app.getEnvironment().getProperty("local.server.port");
      HttpResponse<String> allowed = preflight(base, "http://dev.local:5173");
      HttpResponse<String> refused = preflight(base, "http://evil.example");

      assertThat(allowed.statusCode()).isEqualTo(200);
      assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin"))
          .hasValue("http://dev.local:5173");
      assertThat(allowed.headers().firstValue("Access-Control-Allow-Methods").orElse(""))
          .contains("POST");
      assertThat(allowed.headers().firstValue("Access-Control-Allow-Headers").orElse(""))
          .containsIgnoringCase("content-type");
      assertThat(refused.statusCode()).isEqualTo(403);
    }
  }

  private HttpResponse<String> preflight(String base, String origin) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create(base + "/api/registrations"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type")
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
