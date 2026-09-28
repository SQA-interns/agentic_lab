package org.example.conference.captcha;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Local/test verifier: accepts exactly one configured token. Never active in production. */
public final class DeterministicCaptchaVerifier implements CaptchaVerifier {

  private final byte[] expected;

  public DeterministicCaptchaVerifier(String expectedToken) {
    this.expected = expectedToken.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public boolean verify(String token, String remoteIp) {
    return token != null && MessageDigest.isEqual(expected, token.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public CaptchaMode mode() {
    return CaptchaMode.TEST;
  }
}
