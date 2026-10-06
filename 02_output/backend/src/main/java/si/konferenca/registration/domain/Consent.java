package si.konferenca.registration.domain;

import java.util.Objects;

/** The mandatory consent and its wording (BR-05, D-13). */
public record Consent(String id, String text) {

  public Consent {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(text, "text");
  }
}
