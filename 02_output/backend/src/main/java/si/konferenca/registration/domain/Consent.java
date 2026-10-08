package si.konferenca.registration.domain;

/** A configured consent with its wording (BR-05, D-12). */
public record Consent(String id, String text, boolean mandatory) {}
