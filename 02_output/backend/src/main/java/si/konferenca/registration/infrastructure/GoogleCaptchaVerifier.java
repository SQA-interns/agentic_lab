package si.konferenca.registration.infrastructure;

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
import si.konferenca.registration.application.CaptchaVerifier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies tokens with the Google reCAPTCHA siteverify endpoint (02_contracts/recaptcha.md). The
 * secret and the token are never logged.
 */
public class GoogleCaptchaVerifier implements CaptchaVerifier {

  private static final Logger LOG = LoggerFactory.getLogger(GoogleCaptchaVerifier.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final URI verifyUrl;
  private final String secretKey;
  private final HttpClient http;
  private final JsonMapper mapper = JsonMapper.builder().build();

  public GoogleCaptchaVerifier(String verifyUrl, String secretKey) {
    this.verifyUrl = URI.create(verifyUrl);
    this.secretKey = secretKey;
    this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  }

  @Override
  public boolean verify(String token, String clientAddress) {
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
            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
            .build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        LOG.warn("reCAPTCHA verification answered HTTP {}", response.statusCode());
        return false;
      }
      JsonNode body = mapper.readTree(response.body());
      return body.path("success").isBoolean() && body.get("success").asBoolean();
    } catch (IOException | JacksonException e) {
      LOG.warn("reCAPTCHA verification failed: {}", e.getClass().getSimpleName());
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
