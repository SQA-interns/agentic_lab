package si.konferenca.registration.domain;

/** The configurable option sets (FORM_SCHEMA "Configurable conference options"). */
public enum OptionCategory {
  WORKSHOP("Workshops"),
  EVENT("Events"),
  MEAL("Meals"),
  OTHER("Other activities");

  private final String heading;

  OptionCategory(String heading) {
    this.heading = heading;
  }

  /** Heading used when options are listed by set (emails, export). */
  public String heading() {
    return heading;
  }
}
