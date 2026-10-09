package si.konferenca.registration.domain;

/** Option categories in display order (BR-04). */
public enum Category {
  WORKSHOP("Workshops"),
  EVENT("Events"),
  MEAL("Meals"),
  OTHER("Other activities");

  private final String heading;

  Category(String heading) {
    this.heading = heading;
  }

  public String heading() {
    return heading;
  }
}
