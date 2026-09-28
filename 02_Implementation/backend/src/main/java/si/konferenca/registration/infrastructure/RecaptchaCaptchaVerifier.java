package si.konferenca.registration.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.service.CaptchaVerifier;

/** Production reCAPTCHA v2 verification against Google's siteverify endpoint. */
@Component
@ConditionalOnProperty(
    name = "app.recaptcha.test-mode",
    havingValue = "false",
    matchIfMissing = true)
public class RecaptchaCaptchaVerifier implements CaptchaVerifier {

  private static final Logger LOG = LoggerFactory.getLogger(RecaptchaCaptchaVerifier.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final RestClient restClient;
  private final String secretKey;
  private final String siteKey;
  private final String verifyUrl;

  @Autowired
  public RecaptchaCaptchaVerifier(AppProperties properties, RestClient.Builder builder) {
    this(properties, builder.requestFactory(timeoutRequestFactory()).build());
  }

  RecaptchaCaptchaVerifier(AppProperties properties, RestClient restClient) {
    AppProperties.Recaptcha recaptcha = properties.recaptcha();
    if (recaptcha.secretKey() == null || recaptcha.secretKey().isBlank()) {
      throw new IllegalStateException(
          "RECAPTCHA_SECRET_KEY must be set when reCAPTCHA test mode is disabled");
    }
    this.restClient = restClient;
    this.secretKey = recaptcha.secretKey();
    this.siteKey = recaptcha.siteKey();
    this.verifyUrl = recaptcha.verifyUrl();
  }

  private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(TIMEOUT);
    requestFactory.setReadTimeout(TIMEOUT);
    return requestFactory;
  }

  @Override
  public boolean verify(String token, String remoteIp) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("secret", secretKey);
    form.add("response", token);
    if (remoteIp != null) {
      form.add("remoteip", remoteIp);
    }
    try {
      SiteVerifyResponse response =
          restClient
              .post()
              .uri(verifyUrl)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(SiteVerifyResponse.class);
      return response != null && response.success();
    } catch (RestClientException e) {
      LOG.warn("reCAPTCHA verification request failed: {}", e.getClass().getName());
      return false;
    }
  }

  @Override
  public CaptchaSettings settings() {
    return new CaptchaSettings(CaptchaSettings.Mode.RECAPTCHA, siteKey);
  }

  /** Relevant part of Google's siteverify response. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record SiteVerifyResponse(boolean success) {}
}
