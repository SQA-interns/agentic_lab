package si.konferenca.registration.domain;

/**
 * Categories of configurable conference options (FORM_SCHEMA "Configurable conference options").
 */
public enum OptionCategory {
  WORKSHOP("Workshops"),
  EVENT("Events"),
  MEAL("Meals"),
  OTHER("Other activities");

  private final String label;

  OptionCategory(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
