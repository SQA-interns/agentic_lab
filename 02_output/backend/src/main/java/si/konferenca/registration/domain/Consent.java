package si.konferenca.registration.domain;

/** A configured consent and its wording (BR-05, D-15). */
public record Consent(String id, String text, boolean mandatory) {}
