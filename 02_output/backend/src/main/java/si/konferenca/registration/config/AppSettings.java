package si.konferenca.registration.config;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import si.konferenca.registration.domain.Text;

/**
 * Settings of docs/02_specification.md section 3, read from the environment by variable name
 * (ES-01), with the startup guards of that section. Secrets have no default.
 */
public record AppSettings(
    String environment,
    String mailFrom,
    String conferenceName,
    List<String> organizerEmails,
    String organizerUsername,
    String organizerPassword,
    boolean organizerHttpsOnly,
    Path conferenceConfigPath,
    Path jsonCopyDir,
    boolean recaptchaTestMode,
    String recaptchaSiteKey,
    String recaptchaSecretKey,
    String recaptchaVerifyUrl,
    String corsAllowedOrigin,
    int registrationsPerMinute,
    int exportsPerMinute,
    int formsPerMinute,
    long maxRequestBytes) {

  static final String PRODUCTION = "production";
  static final String DEFAULT_MAIL_FROM = "registration@localhost.localdomain";
  private static final List<String> ENVIRONMENTS = List.of(PRODUCTION, "local", "test");
  private static final Pattern EMAIL =
      Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", Pattern.UNICODE_CHARACTER_CLASS);
  private static final int MIN_PASSWORD_LENGTH = 16;

  public AppSettings {
    organizerEmails = List.copyOf(organizerEmails);
  }

  /** Reads and checks the settings; throws {@link IllegalStateException} naming the setting. */
  public static AppSettings from(Environment env) {
    AppSettings settings =
        new AppSettings(
            optional(env, "APP_ENVIRONMENT", PRODUCTION).toLowerCase(Locale.ROOT),
            optional(env, "MAIL_FROM", DEFAULT_MAIL_FROM),
            optional(env, "CONFERENCE_NAME", "Conference"),
            Arrays.stream(required(env, "ORGANIZER_EMAILS").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList(),
            required(env, "ORGANIZER_USERNAME"),
            required(env, "ORGANIZER_PASSWORD"),
            Boolean.parseBoolean(optional(env, "ORGANIZER_HTTPS_ONLY", "true")),
            Path.of(required(env, "CONFERENCE_CONFIG_PATH")),
            Path.of(optional(env, "JSON_COPY_DIR", "/data/json-copies")),
            Boolean.parseBoolean(optional(env, "RECAPTCHA_TEST_MODE", "false")),
            optional(env, "RECAPTCHA_SITE_KEY", ""),
            optional(env, "RECAPTCHA_SECRET_KEY", ""),
            optional(
                env, "RECAPTCHA_VERIFY_URL", "https://www.google.com/recaptcha/api/siteverify"),
            optional(env, "CORS_ALLOWED_ORIGIN", ""),
            positive(env, "RATE_LIMIT_REGISTRATIONS_PER_MINUTE", "10"),
            positive(env, "RATE_LIMIT_EXPORTS_PER_MINUTE", "10"),
            positive(env, "RATE_LIMIT_FORMS_PER_MINUTE", "60"),
            positive(env, "MAX_REQUEST_BYTES", "16384"));
    settings.check();
    return settings;
  }

  public boolean production() {
    return PRODUCTION.equals(environment);
  }

  private void check() {
    require(ENVIRONMENTS.contains(environment), "APP_ENVIRONMENT must be one of " + ENVIRONMENTS);
    require(!organizerEmails.isEmpty(), "ORGANIZER_EMAILS is empty");
    organizerEmails.forEach(e -> require(validEmail(e), "ORGANIZER_EMAILS has an invalid address"));
    require(validEmail(mailFrom), "MAIL_FROM is not a valid address");
    require(
        !conferenceName.isEmpty() && !Text.hasControlCharacter(conferenceName),
        "CONFERENCE_NAME is empty or has control characters");
    require(
        organizerPassword.length() >= MIN_PASSWORD_LENGTH,
        "ORGANIZER_PASSWORD must have at least " + MIN_PASSWORD_LENGTH + " characters");
    require(
        organizerHttpsOnly || !production(),
        "ORGANIZER_HTTPS_ONLY=false is allowed only when APP_ENVIRONMENT is local or test");
    require(
        !(recaptchaTestMode && production()),
        "RECAPTCHA_TEST_MODE must be off when APP_ENVIRONMENT=production");
    require(
        recaptchaTestMode || (!recaptchaSiteKey.isEmpty() && !recaptchaSecretKey.isEmpty()),
        "RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY are required unless test mode is on");
  }

  private static boolean validEmail(String value) {
    return value.length() <= 254
        && !Text.hasControlCharacter(value)
        && EMAIL.matcher(value).matches();
  }

  /** The value, or the fallback when the variable is missing or blank. */
  private static String optional(Environment env, String name, String fallback) {
    String value = env.getProperty(name, "").trim();
    return value.isEmpty() ? fallback : value;
  }

  private static String required(Environment env, String name) {
    String value = env.getProperty(name, "").trim();
    require(!value.isEmpty(), name + " is required");
    return value;
  }

  private static int positive(Environment env, String name, String fallback) {
    try {
      int value = Integer.parseInt(optional(env, name, fallback));
      require(value > 0, name + " must be positive");
      return value;
    } catch (NumberFormatException e) {
      throw new IllegalStateException(name + " must be a number", e);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException("Invalid configuration: " + message);
    }
  }

  @Override
  public String toString() {
    return "AppSettings[environment=" + environment + "]";
  }
}
