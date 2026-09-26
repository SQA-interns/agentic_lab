package si.konferenca.registration.domain;

/** A configurable conference option such as a workshop, event, meal or other activity. */
public record ConferenceOption(String id, String name, OptionCategory category, boolean active) {}
