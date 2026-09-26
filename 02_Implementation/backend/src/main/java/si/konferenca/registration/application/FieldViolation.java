package si.konferenca.registration.application;

/** A validation problem attached to one input field. */
public record FieldViolation(String field, String message) {}
