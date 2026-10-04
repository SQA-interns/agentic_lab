package si.konferenca.registration.application;

import java.io.Serializable;

/** A rejected request field and the reason code of the REST contract. */
public record FieldError(String field, String code) implements Serializable {}
