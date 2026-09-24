package org.conference.registration.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.conference.registration.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Signed, single-use form tokens (anti-automation, AC-PC-04). A token encodes its issue time and a
 * random nonce, signed with HMAC-SHA256. A submission is rejected if the token is forged, younger
 * than the minimum fill time, older than the maximum age, or was already used.
 */
@Service
public class FormTokenService {

  private static final Logger LOG = LoggerFactory.getLogger(FormTokenService.class);
  private static final String HMAC = "HmacSHA256";
  private static final int MIN_SECRET_BYTES = 32;
  private static final int CLEANUP_THRESHOLD = 10_000;
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64D = Base64.getUrlDecoder();

  private final SecureRandom random = new SecureRandom();
  private final byte[] secret;
  private final long minFillMillis;
  private final long maxAgeMillis;
  private final Clock clock;
  private final Map<String, Long> usedNonces = new ConcurrentHashMap<>();

  public FormTokenService(AppProperties properties, Clock clock) {
    this.clock = clock;
    this.minFillMillis = properties.antibot().minFillSeconds() * 1000;
    this.maxAgeMillis = properties.antibot().maxAgeSeconds() * 1000;
    String configured = properties.antibot().formTokenSecret();
    if (configured == null || configured.isBlank()) {
      LOG.warn("APP_FORM_TOKEN_SECRET not set; using a random per-process secret");
      this.secret = new byte[MIN_SECRET_BYTES];
      random.nextBytes(secret);
    } else {
      this.secret = configured.getBytes(StandardCharsets.UTF_8);
      if (secret.length < MIN_SECRET_BYTES) {
        throw new IllegalStateException("Form token secret must be at least 32 bytes");
      }
    }
  }

  /** Issues a new token for a form that is being displayed now. */
  public String issue() {
    byte[] nonceBytes = new byte[16];
    random.nextBytes(nonceBytes);
    String payload = clock.millis() + "." + B64.encodeToString(nonceBytes);
    return B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + sign(payload);
  }

  /** Checks signature and age; does not consume the token. */
  public VerifiedToken verify(String token) {
    if (token == null) {
      throw new SubmissionRejectedException("missing form token");
    }
    String[] parts = token.split("\\.", -1);
    if (parts.length != 2) {
      throw new SubmissionRejectedException("malformed form token");
    }
    String payload;
    try {
      payload = new String(B64D.decode(parts[0]), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new SubmissionRejectedException("malformed form token");
    }
    byte[] expected = sign(payload).getBytes(StandardCharsets.US_ASCII);
    if (!MessageDigest.isEqual(expected, parts[1].getBytes(StandardCharsets.US_ASCII))) {
      throw new SubmissionRejectedException("bad form token signature");
    }
    int dot = payload.indexOf('.');
    long issuedAt;
    try {
      issuedAt = Long.parseLong(payload.substring(0, dot));
    } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
      throw new SubmissionRejectedException("malformed form token");
    }
    long age = clock.millis() - issuedAt;
    if (age < minFillMillis) {
      throw new SubmissionRejectedException("form submitted too fast");
    }
    if (age > maxAgeMillis) {
      throw new SubmissionRejectedException("form token expired");
    }
    String nonce = payload.substring(dot + 1);
    if (usedNonces.containsKey(nonce)) {
      throw new SubmissionRejectedException("form token already used");
    }
    return new VerifiedToken(nonce, issuedAt);
  }

  /** Atomically marks the token as used; fails if another request consumed it first. */
  public void consume(VerifiedToken token) {
    long expiresAt = token.issuedAt() + maxAgeMillis;
    if (usedNonces.putIfAbsent(token.nonce(), expiresAt) != null) {
      throw new SubmissionRejectedException("form token already used");
    }
    if (usedNonces.size() > CLEANUP_THRESHOLD) {
      long now = clock.millis();
      usedNonces.values().removeIf(expiry -> expiry < now);
    }
  }

  private String sign(String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(secret, HMAC));
      return B64.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC unavailable", e);
    }
  }

  /** A token whose signature and age were verified. */
  public record VerifiedToken(String nonce, long issuedAt) {}
}
