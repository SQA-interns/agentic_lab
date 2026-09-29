package si.konferenca.registration.domain;

/** The two registration forms (BUSINESS_RULES "Registration types"). */
public enum RegistrationType {
  EXTERNAL("External participant", "External"),
  STUDENT("Student", "Student");

  private final String label;
  private final String shortLabel;

  RegistrationType(String label, String shortLabel) {
    this.label = label;
    this.shortLabel = shortLabel;
  }

  /** Human-readable name used in emails. */
  public String label() {
    return label;
  }

  /** Short name used in the export's Type column. */
  public String shortLabel() {
    return shortLabel;
  }
}
