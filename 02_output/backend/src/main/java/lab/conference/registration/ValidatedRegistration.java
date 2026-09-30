package lab.conference.registration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lab.conference.options.CatalogOption;
import lab.conference.options.GroupId;

/**
 * A submission that passed field validation: trimmed values in canonical field order, resolved
 * active options per group, consent state (null when no consent is configured).
 */
public record ValidatedRegistration(
    UUID clientRequestId,
    FormType formType,
    Map<String, String> fields,
    Map<GroupId, List<CatalogOption>> selections,
    ConsentState consent,
    String captchaToken) {

  /** Consent fixture ID and the participant's answer. */
  public record ConsentState(String id, boolean given) {}

  public String field(String name) {
    return fields.get(name);
  }

  /** SHA-256 over the normalised content; identical retries have identical fingerprints. */
  public String fingerprint() {
    StringBuilder sb = new StringBuilder(formType.key()).append('\u0000');
    fields.forEach((k, v) -> sb.append(k).append('=').append(v).append('\u0000'));
    selections.forEach(
        (g, opts) -> {
          sb.append(g.key()).append(':');
          opts.forEach(o -> sb.append(o.id()).append(','));
          sb.append('\u0000');
        });
    sb.append("consent=").append(consent == null ? "-" : consent.id() + ":" + consent.given());
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
