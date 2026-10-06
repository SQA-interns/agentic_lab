package si.konferenca.registration.domain;

/** The two fixed registration forms (BR-01). */
public enum RegistrationType {
  EXTERNAL("External participant"),
  STUDENT("Student");

  private final String label;

  RegistrationType(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
