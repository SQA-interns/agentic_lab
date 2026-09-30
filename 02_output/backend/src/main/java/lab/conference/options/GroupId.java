package lab.conference.options;

import java.util.Optional;

/** The four fixed option groups (BR-03), in display order. */
public enum GroupId {
  WORKSHOPS("workshops", "Workshops"),
  EVENTS("events", "Events"),
  MEALS("meals", "Meals"),
  OTHER("other", "Other activities");

  private final String key;
  private final String label;

  GroupId(String key, String label) {
    this.key = key;
    this.label = label;
  }

  public String key() {
    return key;
  }

  public String label() {
    return label;
  }

  public static Optional<GroupId> fromKey(String key) {
    for (GroupId g : values()) {
      if (g.key.equals(key)) {
        return Optional.of(g);
      }
    }
    return Optional.empty();
  }
}
