package si.konferenca.registration.infrastructure.captcha;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.konferenca.registration.application.RegistrationPorts.CaptchaResult;
import si.konferenca.registration.application.RegistrationPorts.CaptchaVerifier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Google reCAPTCHA v2 verification (SR-01, docs/02_contracts/recaptcha-siteverify.openapi.yaml). A
 * timeout, a non-200 status or an unreadable answer counts as unavailable (fail closed).
 */
public class RecaptchaVerifier implements CaptchaVerifier {

  private static final Logger LOG = LoggerFactory.getLogger(RecaptchaVerifier.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final URI verifyUrl;
  private final String secretKey;
  private final HttpClient client;

  public RecaptchaVerifier(String verifyUrl, String secretKey) {
    this.verifyUrl = URI.create(verifyUrl);
    this.secretKey = secretKey;
    this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  }

  @Override
  public CaptchaResult verify(String token) {
    String form =
        "secret="
            + URLEncoder.encode(secretKey, StandardCharsets.UTF_8)
            + "&response="
            + URLEncoder.encode(token, StandardCharsets.UTF_8);
    HttpRequest request =
        HttpRequest.newBuilder(verifyUrl)
            .timeout(TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        LOG.warn("Anti-automation verification answered status {}", response.statusCode());
        return CaptchaResult.UNAVAILABLE;
      }
      JsonNode answer = JSON.readTree(response.body());
      JsonNode success = answer.get("success");
      if (success == null || !success.isBoolean()) {
        LOG.warn("Anti-automation verification answer has no success flag");
        return CaptchaResult.UNAVAILABLE;
      }
      return success.booleanValue() ? CaptchaResult.PASSED : CaptchaResult.FAILED;
    } catch (IOException | JacksonException e) {
      LOG.warn("Anti-automation verification unavailable: {}", e.getClass().getSimpleName());
      return CaptchaResult.UNAVAILABLE;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return CaptchaResult.UNAVAILABLE;
    }
  }
}
