package si.konferenca.registration.domain;

import java.io.Serializable;

/** A validation error for one request field, named as in the REST contract. */
public record FieldError(String field, String message) implements Serializable {}
