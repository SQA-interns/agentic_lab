package lab.conference.registration;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Google reCAPTCHA server verification; fails closed on any error or non-success answer. */
public final class RecaptchaVerifier implements CaptchaVerifier {

  static final URI VERIFY_URI = URI.create("https://www.google.com/recaptcha/api/siteverify");
  private static final Logger LOG = LoggerFactory.getLogger(RecaptchaVerifier.class);
  private final String siteKey;
  private final String secret;
  private final HttpClient client;
  private final URI verifyUri;
  private final ObjectMapper json = new ObjectMapper();

  public RecaptchaVerifier(String siteKey, String secret) {
    this(
        siteKey,
        secret,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
        VERIFY_URI);
  }

  RecaptchaVerifier(String siteKey, String secret, HttpClient client, URI verifyUri) {
    this.siteKey = siteKey;
    this.secret = secret;
    this.client = client;
    this.verifyUri = verifyUri;
  }

  @Override
  public String mode() {
    return "recaptcha";
  }

  @Override
  public String siteKey() {
    return siteKey;
  }

  @Override
  public boolean verify(String token, String remoteAddress) {
    String form =
        "secret=" + enc(secret) + "&response=" + enc(token) + "&remoteip=" + enc(remoteAddress);
    HttpRequest request =
        HttpRequest.newBuilder(verifyUri)
            .timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return false;
      }
      JsonNode body = json.readTree(response.body());
      return body.path("success").asBoolean(false);
    } catch (IOException e) {
      LOG.warn("Captcha verification unavailable ({})", e.getClass().getSimpleName());
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
