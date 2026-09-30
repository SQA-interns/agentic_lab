package lab.conference.platform;

import java.io.Serializable;

/** One field-level validation error (openapi.yaml FieldError). */
public record FieldError(String field, String code, String message) implements Serializable {}
