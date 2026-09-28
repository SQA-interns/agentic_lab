package si.konferenca.registration.service;

/** A validation problem of one request field. */
public record FieldViolation(String field, String code) {}
