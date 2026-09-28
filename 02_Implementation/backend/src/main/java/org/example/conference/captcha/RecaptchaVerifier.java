package org.example.conference.captcha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Google reCAPTCHA v2 server-side verification. Fails closed on any error. */
public final class RecaptchaVerifier implements CaptchaVerifier {

  private static final Logger LOG = LoggerFactory.getLogger(RecaptchaVerifier.class);
  private static final int MAX_TOKEN_LENGTH = 4096;

  private final RestClient restClient;
  private final String verifyUrl;
  private final String secret;

  public RecaptchaVerifier(RestClient restClient, String verifyUrl, String secret) {
    this.restClient = restClient;
    this.verifyUrl = verifyUrl;
    this.secret = secret;
  }

  @Override
  public boolean verify(String token, String remoteIp) {
    if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
      return false;
    }
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("secret", secret);
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
      LOG.warn("reCAPTCHA verification unavailable: {}", e.getClass().getSimpleName());
      return false;
    }
  }

  @Override
  public CaptchaMode mode() {
    return CaptchaMode.RECAPTCHA;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record SiteVerifyResponse(boolean success) {}
}
