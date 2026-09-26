package si.konferenca.registration.infrastructure.captcha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import si.konferenca.registration.application.CaptchaFailedException;
import si.konferenca.registration.application.CaptchaUnavailableException;
import si.konferenca.registration.application.port.CaptchaVerifier;
import si.konferenca.registration.config.AppProperties;

/**
 * Google reCAPTCHA v2 server-side verification. In test mode (enabled only through environment
 * configuration) no network call is made and only {@link #TEST_MODE_TOKEN} is accepted.
 */
@Component
public class RecaptchaVerifier implements CaptchaVerifier {

  public static final String TEST_MODE_TOKEN = "test-mode-token";

  private static final Logger LOG = LoggerFactory.getLogger(RecaptchaVerifier.class);
  private static final int MAX_TOKEN_LENGTH = 4096;

  private final boolean testMode;
  private final String secretKey;
  private final String verifyUrl;
  private final RestClient restClient;

  public RecaptchaVerifier(AppProperties properties) {
    AppProperties.Recaptcha config = properties.recaptcha();
    this.testMode = config.testMode();
    this.secretKey = config.secretKey();
    this.verifyUrl = config.verifyUrl();
    if (testMode) {
      LOG.warn("reCAPTCHA TEST MODE is enabled - never use this setting in production");
    } else if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalStateException(
          "RECAPTCHA_SECRET_KEY must be configured when reCAPTCHA test mode is disabled");
    }
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(5));
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  @Override
  public void verify(String token, String clientIp) {
    if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
      throw new CaptchaFailedException();
    }
    if (testMode) {
      if (!TEST_MODE_TOKEN.equals(token)) {
        throw new CaptchaFailedException();
      }
      return;
    }
    SiteVerifyResponse response = callSiteVerify(token, clientIp);
    if (response == null || !response.success()) {
      throw new CaptchaFailedException();
    }
  }

  private SiteVerifyResponse callSiteVerify(String token, String clientIp) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("secret", secretKey);
    form.add("response", token);
    if (clientIp != null) {
      form.add("remoteip", clientIp);
    }
    try {
      return restClient
          .post()
          .uri(verifyUrl)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .body(SiteVerifyResponse.class);
    } catch (RestClientException e) {
      throw new CaptchaUnavailableException(e);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record SiteVerifyResponse(boolean success) {}
}
