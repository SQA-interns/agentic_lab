package si.konferenca.registration.domain;

/** A configured consent with its wording (BR-05, D-10). */
public record ConsentDefinition(String id, String text, boolean mandatory) {}
