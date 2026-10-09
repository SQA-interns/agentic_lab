package si.konferenca.registration.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import si.konferenca.registration.application.CaptchaVerifier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies reCAPTCHA v2 tokens with Google (docs/02_contracts/recaptcha-siteverify.yaml; SR-01).
 * The secret is sent only in the request body and never logged.
 */
public class RecaptchaCaptchaVerifier implements CaptchaVerifier {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final URI verifyUrl;
  private final String secretKey;
  private final HttpClient http;

  public RecaptchaCaptchaVerifier(URI verifyUrl, String secretKey) {
    this.verifyUrl = verifyUrl;
    this.secretKey = secretKey;
    this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  }

  @Override
  public void verify(String token, String clientIp) {
    if (token == null || token.isBlank()) {
      throw new CaptchaFailedException();
    }
    String form =
        "secret="
            + encode(secretKey)
            + "&response="
            + encode(token)
            + (clientIp == null ? "" : "&remoteip=" + encode(clientIp));
    HttpRequest request =
        HttpRequest.newBuilder(verifyUrl)
            .timeout(TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
            .build();
    JsonNode answer;
    try {
      HttpResponse<String> response =
          http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) {
        throw new CaptchaUnavailableException(
            new IOException("verification answered " + response.statusCode()));
      }
      answer = JSON.readTree(response.body());
    } catch (IOException | JacksonException e) {
      throw new CaptchaUnavailableException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CaptchaUnavailableException(e);
    }
    if (!answer.path("success").asBoolean(false)) {
      throw new CaptchaFailedException();
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
