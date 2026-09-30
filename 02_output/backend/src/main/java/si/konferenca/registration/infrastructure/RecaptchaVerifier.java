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
import tools.jackson.databind.json.JsonMapper;

/** Production reCAPTCHA v2 verification against Google's siteverify endpoint (SR-01). */
public class RecaptchaVerifier implements CaptchaVerifier {

  private static final Logger LOG = LoggerFactory.getLogger(RecaptchaVerifier.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final URI verifyUrl;
  private final String secret;
  private final HttpClient client;
  private final JsonMapper mapper = JsonMapper.builder().build();

  public RecaptchaVerifier(URI verifyUrl, String secret) {
    this.verifyUrl = verifyUrl;
    this.secret = secret;
    this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  }

  @Override
  public boolean verify(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    String form = "secret=" + encode(secret) + "&response=" + encode(token);
    HttpRequest request =
        HttpRequest.newBuilder(verifyUrl)
            .timeout(TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    try {
      HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        LOG.warn("reCAPTCHA verification answered HTTP {}", response.statusCode());
        return false;
      }
      return mapper.readTree(response.body()).path("success").asBoolean(false);
    } catch (IOException | JacksonException e) {
      LOG.warn("reCAPTCHA verification failed: {}", e.getClass().getSimpleName());
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @Override
  public boolean testMode() {
    return false;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
