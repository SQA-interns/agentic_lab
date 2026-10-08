package si.konferenca.registration.infrastructure;

import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import si.konferenca.registration.config.AppProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies anti-automation tokens on the server (SR-01, recaptcha-verify.openapi.yaml). Fails
 * closed: any error rejects the token. Test mode (SR-02) makes no call.
 */
@Component
public class RecaptchaVerifier {

  /** The only token accepted in test mode (ui-form.json). */
  public static final String TEST_MODE_TOKEN = "test-mode-pass";

  private static final Logger LOG = LoggerFactory.getLogger(RecaptchaVerifier.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final AppProperties.Recaptcha settings;
  private final RestClient client;
  private final JsonMapper mapper = JsonMapper.builder().build();

  public RecaptchaVerifier(AppProperties properties) {
    this.settings = properties.recaptcha();
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    factory.setReadTimeout(TIMEOUT);
    this.client = RestClient.builder().requestFactory(factory).build();
  }

  public boolean verify(String token, String remoteIp) {
    if (token == null || token.isBlank()) {
      return false;
    }
    if (settings.testMode()) {
      return TEST_MODE_TOKEN.equals(token);
    }
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("secret", settings.secretKey());
    form.add("response", token);
    if (remoteIp != null) {
      form.add("remoteip", remoteIp);
    }
    try {
      String body =
          client
              .post()
              .uri(settings.verifyUrl())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(String.class);
      JsonNode result = mapper.readTree(body == null ? "{}" : body);
      return result.path("success").isBoolean() && result.path("success").asBoolean();
    } catch (RuntimeException e) {
      LOG.warn("reCAPTCHA verification failed: {}", e.getClass().getName());
      return false;
    }
  }
}
