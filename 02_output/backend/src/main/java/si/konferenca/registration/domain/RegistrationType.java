package si.konferenca.registration.domain;

/** The two registration types (BR-01) with their user-facing label. */
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
