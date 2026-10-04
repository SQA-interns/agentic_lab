package si.konferenca.registration.domain;

/** The two registration types of BR-01. */
public enum RegistrationType {
  EXTERNAL("External participant", "external participant"),
  STUDENT("Student", "student");

  private final String label;
  private final String lowerLabel;

  RegistrationType(String label, String lowerLabel) {
    this.label = label;
    this.lowerLabel = lowerLabel;
  }

  /** Label as shown in the export and emails, e.g. "External participant". */
  public String label() {
    return label;
  }

  /** Label inside a sentence, e.g. "external participant". */
  public String lowerLabel() {
    return lowerLabel;
  }
}
