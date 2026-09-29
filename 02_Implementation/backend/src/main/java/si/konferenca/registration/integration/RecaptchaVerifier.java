package si.konferenca.registration.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Google reCAPTCHA v2 server-side verification (specification §8.2). Fails closed: any error,
 * timeout or non-success reply is a failed verification.
 */
public class RecaptchaVerifier {

  /** The only token accepted in deterministic test mode. */
  public static final String TEST_PASS_TOKEN = "test-pass";

  private static final int MAX_TOKEN_LENGTH = 4096;

  private final boolean testMode;
  private final String secretKey;
  private final URI verifyUrl;
  private final HttpClient httpClient;
  private final ObjectMapper mapper = new ObjectMapper();

  public RecaptchaVerifier(boolean testMode, String secretKey, URI verifyUrl) {
    this(
        testMode,
        secretKey,
        verifyUrl,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
  }

  RecaptchaVerifier(boolean testMode, String secretKey, URI verifyUrl, HttpClient httpClient) {
    this.testMode = testMode;
    this.secretKey = secretKey;
    this.verifyUrl = verifyUrl;
    this.httpClient = httpClient;
  }

  public boolean isTestMode() {
    return testMode;
  }

  public boolean verify(String token, String remoteIp) {
    if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
      return false;
    }
    if (testMode) {
      return TEST_PASS_TOKEN.equals(token);
    }
    return verifyWithProvider(token, remoteIp);
  }

  private boolean verifyWithProvider(String token, String remoteIp) {
    String form =
        "secret="
            + encode(secretKey)
            + "&response="
            + encode(token)
            + (remoteIp == null ? "" : "&remoteip=" + encode(remoteIp));
    HttpRequest request =
        HttpRequest.newBuilder(verifyUrl)
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
            .build();
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) {
        return false;
      }
      JsonNode body = mapper.readTree(response.body());
      return body != null && body.path("success").isBoolean() && body.path("success").asBoolean();
    } catch (IOException | RuntimeException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
