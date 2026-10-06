package si.konferenca.registration.infrastructure.recaptcha;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import si.konferenca.registration.application.CaptchaVerifier;
import si.konferenca.registration.application.ServiceUnavailableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Google reCAPTCHA v2 verification, or the deterministic test mode (recaptcha.schema.json). */
public class RecaptchaVerifier implements CaptchaVerifier {

  /** The only token the test mode accepts. */
  public static final String TEST_PASS_TOKEN = "test-pass";

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final boolean testMode;
  private final String siteKey;
  private final String secretKey;
  private final URI verifyUrl;
  private final HttpClient http;
  private final JsonMapper mapper = JsonMapper.builder().build();

  public RecaptchaVerifier(boolean testMode, String siteKey, String secretKey, String verifyUrl) {
    this.testMode = testMode;
    this.siteKey = siteKey == null ? "" : siteKey;
    this.secretKey = secretKey == null ? "" : secretKey;
    this.verifyUrl = URI.create(verifyUrl);
    this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  }

  @Override
  public boolean testMode() {
    return testMode;
  }

  @Override
  public String siteKey() {
    return siteKey;
  }

  @Override
  public boolean verify(String token, String clientAddress) {
    if (token == null || token.isBlank()) {
      return false;
    }
    if (testMode) {
      return TEST_PASS_TOKEN.equals(token);
    }
    String form =
        "secret="
            + encode(secretKey)
            + "&response="
            + encode(token)
            + (clientAddress == null ? "" : "&remoteip=" + encode(clientAddress));
    HttpRequest request =
        HttpRequest.newBuilder(verifyUrl)
            .timeout(TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new ServiceUnavailableException(
            "reCAPTCHA verification returned " + response.statusCode(), null);
      }
      JsonNode body = mapper.readTree(response.body());
      return body.path("success").asBoolean(false);
    } catch (java.io.IOException | tools.jackson.core.JacksonException e) {
      throw new ServiceUnavailableException("reCAPTCHA verification unavailable", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ServiceUnavailableException("reCAPTCHA verification interrupted", e);
    }
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
