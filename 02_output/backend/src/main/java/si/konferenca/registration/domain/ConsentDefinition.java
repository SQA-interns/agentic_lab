package si.konferenca.registration.domain;

import java.util.Objects;

/** A configured consent with its wording (BR-05, D-06). */
public record ConsentDefinition(String id, String text, boolean mandatory) {

  public ConsentDefinition {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(text, "text");
  }
}
