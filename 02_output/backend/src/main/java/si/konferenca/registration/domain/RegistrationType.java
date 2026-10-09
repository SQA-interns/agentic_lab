package si.konferenca.registration.domain;

/** The two registration types (BR-01). */
public enum RegistrationType {
  EXTERNAL("External participant"),
  STUDENT("Student");

  private final String label;

  RegistrationType(String label) {
    this.label = label;
  }

  /** Human-readable name used in emails and the export. */
  public String label() {
    return label;
  }
}
