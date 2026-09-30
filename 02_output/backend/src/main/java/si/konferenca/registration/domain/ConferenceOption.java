package si.konferenca.registration.domain;

/** A configured workshop, event, meal or other activity (BR-04, US-003). */
public record ConferenceOption(String id, String name, OptionCategory category, boolean active) {}
