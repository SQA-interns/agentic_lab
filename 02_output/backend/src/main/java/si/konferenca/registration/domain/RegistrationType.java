package si.konferenca.registration.domain;

import java.util.List;

/** The registration types and the fields each one asks for (BR-01). */
public enum RegistrationType {
  EXTERNAL(
      List.of(TextField.FIRST_NAME, TextField.LAST_NAME, TextField.EMAIL, TextField.ORGANIZATION));

  private final List<TextField> fields;

  RegistrationType(List<TextField> fields) {
    this.fields = List.copyOf(fields);
  }

  /** The required fields of this type, in form order. */
  public List<TextField> fields() {
    return fields;
  }
}
