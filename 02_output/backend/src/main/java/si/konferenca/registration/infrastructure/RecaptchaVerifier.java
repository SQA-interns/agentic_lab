package si.konferenca.registration.infrastructure;

import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import si.konferenca.registration.application.CaptchaVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies reCAPTCHA v2 tokens with Google (recaptcha-siteverify.openapi.yaml). Only {@code
 * success: true} passes; errors, timeouts and unreadable answers fail the check.
 */
public final class RecaptchaVerifier implements CaptchaVerifier {

  private static final Logger LOG = LoggerFactory.getLogger(RecaptchaVerifier.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final RestClient rest;
  private final JsonMapper mapper;
  private final String verifyUrl;
  private final String secretKey;

  public RecaptchaVerifier(String verifyUrl, String secretKey, JsonMapper mapper) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(TIMEOUT);
    this.rest = RestClient.builder().requestFactory(factory).build();
    this.mapper = mapper;
    this.verifyUrl = verifyUrl;
    this.secretKey = secretKey;
  }

  @Override
  public boolean verify(String token, String clientAddress) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("secret", secretKey);
    form.add("response", token);
    if (clientAddress != null) {
      form.add("remoteip", clientAddress);
    }
    try {
      String body =
          rest.post()
              .uri(verifyUrl)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(String.class);
      JsonNode success = mapper.readTree(body == null ? "{}" : body).path("success");
      return success.isBoolean() && success.asBoolean();
    } catch (RuntimeException e) {
      LOG.warn("anti-automation verification failed: {}", e.getClass().getSimpleName());
      return false;
    }
  }
}
