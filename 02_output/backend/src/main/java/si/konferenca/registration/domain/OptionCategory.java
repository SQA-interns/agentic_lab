package si.konferenca.registration.domain;

/** Groups of conference options (BR-04), in display order. */
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
