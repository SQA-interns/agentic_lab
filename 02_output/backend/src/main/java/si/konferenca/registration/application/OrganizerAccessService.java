package si.konferenca.registration.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Organizer login for the export (BR-08, SB-02, SB-03): the configured password is kept only as a
 * BCrypt hash; issued tokens are random, valid for 15 minutes and kept only as SHA-256 hashes.
 */
public class OrganizerAccessService {

  static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
  private static final int TOKEN_BYTES = 32;

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Instant> tokens = new ConcurrentHashMap<>();
  private final byte[] username;
  private final String passwordHash;
  private final boolean httpsOnly;
  private final Clock clock;

  public OrganizerAccessService(String username, String password, boolean httpsOnly, Clock clock) {
    this.username = username.getBytes(StandardCharsets.UTF_8);
    this.passwordHash = encoder.encode(password);
    this.httpsOnly = httpsOnly;
    this.clock = clock;
  }

  /** An issued token and its expiry. */
  public record Token(String value, Instant expiresAt) {}

  /** Whether organizer credentials are accepted only over HTTPS or from localhost (SR-06). */
  public boolean httpsOnly() {
    return httpsOnly;
  }

  /** Issues a token for correct credentials; both checks always run (no timing hint). */
  public Optional<Token> issue(String user, String password) {
    boolean userMatches =
        MessageDigest.isEqual(username, String.valueOf(user).getBytes(StandardCharsets.UTF_8));
    boolean passwordMatches = encoder.matches(String.valueOf(password), passwordHash);
    if (!userMatches || !passwordMatches) {
      return Optional.empty();
    }
    Instant now = clock.instant();
    tokens.values().removeIf(expiry -> !expiry.isAfter(now));
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant expiresAt = now.plus(TOKEN_LIFETIME);
    tokens.put(hash(value), expiresAt);
    return Optional.of(new Token(value, expiresAt));
  }

  /** True for an issued, unexpired token. */
  public boolean isValid(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    Instant expiry = tokens.get(hash(token));
    return expiry != null && expiry.isAfter(clock.instant());
  }

  private static String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
