package si.konferenca.registration.domain;

/** Option categories in display order (BR-04) with their user-facing label. */
public enum Category {
  WORKSHOP("Workshops"),
  EVENT("Events"),
  MEAL("Meals"),
  OTHER("Other activities");

  private final String label;

  Category(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
