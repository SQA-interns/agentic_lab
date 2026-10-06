package si.konferenca.registration.integration;

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
import org.springframework.stereotype.Component;
import si.konferenca.registration.config.AppProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the anti-automation token on the trusted side (SR-01). In test mode (SR-02, only in
 * local and test environments) only {@value #TEST_MODE_TOKEN} passes and nothing is called.
 */
@Component
public class AntiAutomationVerifier {

  /** The deterministic test-mode token (`recaptcha-siteverify.openapi.yaml`). */
  public static final String TEST_MODE_TOKEN = "test-mode-pass";

  private static final int MAX_TOKEN_LENGTH = 4096;

  /** Result of a verification. */
  public enum Outcome {
    PASSED,
    FAILED,
    UNAVAILABLE
  }

  private static final Logger LOG = LoggerFactory.getLogger(AntiAutomationVerifier.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final AppProperties.Recaptcha settings;
  private final HttpClient client;

  public AntiAutomationVerifier(AppProperties app) {
    this.settings = app.recaptcha();
    this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  }

  public Outcome verify(String token, String remoteAddress) {
    if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
      return Outcome.FAILED;
    }
    if (settings.testMode()) {
      return TEST_MODE_TOKEN.equals(token) ? Outcome.PASSED : Outcome.FAILED;
    }
    return siteVerify(token, remoteAddress);
  }

  /** POST to siteverify (`recaptcha-siteverify.openapi.yaml`); only success=true passes. */
  private Outcome siteVerify(String token, String remoteAddress) {
    String form =
        "secret="
            + encode(settings.secretKey())
            + "&response="
            + encode(token)
            + (remoteAddress == null ? "" : "&remoteip=" + encode(remoteAddress));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(settings.verifyUrl()))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
            .build();
    try {
      HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() / 100 != 2) {
        LOG.warn("anti-automation verification returned HTTP {}", response.statusCode());
        return Outcome.UNAVAILABLE;
      }
      JsonNode body = MAPPER.readTree(response.body());
      return body.path("success").asBoolean(false) ? Outcome.PASSED : Outcome.FAILED;
    } catch (IOException | JacksonException e) {
      LOG.warn("anti-automation verification unavailable ({})", e.getClass().getSimpleName());
      return Outcome.UNAVAILABLE;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Outcome.UNAVAILABLE;
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
