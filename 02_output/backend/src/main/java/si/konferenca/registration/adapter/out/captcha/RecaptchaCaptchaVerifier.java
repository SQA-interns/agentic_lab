package si.konferenca.registration.adapter.out.captcha;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import si.konferenca.registration.domain.CaptchaVerifier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies a reCAPTCHA v2 token with the verification service (recaptcha-verify.schema.json). Only
 * the secret and the token are sent, never the client address (SB-12).
 */
public class RecaptchaCaptchaVerifier implements CaptchaVerifier {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final int HTTP_OK_FIRST = 200;
  private static final int HTTP_OK_LAST = 299;

  private final URI verifyUri;
  private final String secretKey;
  private final HttpClient client;
  private final JsonMapper json = JsonMapper.builder().build();

  public RecaptchaCaptchaVerifier(String verifyUrl, String secretKey) {
    this.verifyUri = URI.create(verifyUrl);
    this.secretKey = secretKey;
    this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  }

  @Override
  public boolean verify(String token) {
    String form =
        "secret="
            + URLEncoder.encode(secretKey, StandardCharsets.UTF_8)
            + "&response="
            + URLEncoder.encode(token, StandardCharsets.UTF_8);
    HttpRequest request =
        HttpRequest.newBuilder(verifyUri)
            .timeout(TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < HTTP_OK_FIRST || response.statusCode() > HTTP_OK_LAST) {
        throw new CaptchaUnavailableException(
            "verification answered with status " + response.statusCode(), null);
      }
      JsonNode success = json.readTree(response.body()).path("success");
      if (!success.isBoolean()) {
        throw new CaptchaUnavailableException("verification answer has no success flag", null);
      }
      return success.asBoolean();
    } catch (IOException | JacksonException e) {
      throw new CaptchaUnavailableException("verification could not be completed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CaptchaUnavailableException("verification was interrupted", e);
    }
  }
}
