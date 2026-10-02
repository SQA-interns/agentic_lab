package si.konferenca.registration.domain;

/** A configured conference option (US-003). */
public record ConferenceOption(String id, String name, OptionCategory category, boolean active) {}
