package si.konferenca.registration.adapter.captcha;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import si.konferenca.registration.application.CaptchaVerifier;

/**
 * Google reCAPTCHA v2 verification (SR-01, docs/02_contracts/recaptcha-verify.openapi.yaml). Any
 * error, timeout or {@code success=false} fails the check.
 */
public class GoogleCaptchaVerifier implements CaptchaVerifier {

  private static final Logger LOG = LoggerFactory.getLogger(GoogleCaptchaVerifier.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final RestClient client;
  private final String verifyUrl;
  private final String secret;

  public GoogleCaptchaVerifier(String verifyUrl, String secret) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(TIMEOUT);
    this.client = RestClient.builder().requestFactory(factory).build();
    this.verifyUrl = verifyUrl;
    this.secret = secret;
  }

  @Override
  public boolean verify(String token, String remoteAddress) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("secret", secret);
    form.add("response", token);
    if (remoteAddress != null) {
      form.add("remoteip", remoteAddress);
    }
    try {
      Map<?, ?> result =
          client
              .post()
              .uri(verifyUrl)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(Map.class);
      return result != null && Boolean.TRUE.equals(result.get("success"));
    } catch (RuntimeException e) {
      LOG.warn("reCAPTCHA verification failed: {}", e.getClass().getSimpleName());
      return false;
    }
  }
}
