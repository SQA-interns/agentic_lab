package si.konferenca.registration.domain;

/** One rejected field with a code from the API contract. */
public record FieldError(String field, String code) {}
