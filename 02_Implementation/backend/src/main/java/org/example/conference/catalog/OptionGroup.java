package org.example.conference.catalog;

/** The four fixed conference-option groups (PRODUCT). */
public enum OptionGroup {
  WORKSHOPS("workshops"),
  EVENTS("events"),
  MEALS("meals"),
  OTHER_ACTIVITIES("otherActivities");

  private final String key;

  OptionGroup(String key) {
    this.key = key;
  }

  /** JSON/config key of the group. */
  public String key() {
    return key;
  }
}
