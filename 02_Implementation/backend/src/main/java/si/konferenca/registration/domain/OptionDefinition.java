package si.konferenca.registration.domain;

/** One entry of the organizer-edited options file (specification §4). */
public record OptionDefinition(String id, OptionCategory category, String name, boolean active) {}
