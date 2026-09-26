package si.konferenca.registration.domain;

/** A consent field shown on the registration forms. */
public record ConsentDefinition(String id, String label, boolean required) {}
