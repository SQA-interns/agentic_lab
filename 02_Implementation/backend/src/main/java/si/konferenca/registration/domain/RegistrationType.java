package si.konferenca.registration.domain;

/** Registration types supported by the conference (BUSINESS_RULES "Registration types"). */
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
