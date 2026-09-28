package org.example.conference.shared.api;

/** A field-level validation error. Never contains the submitted value. */
public record FieldError(String field, String code) {}
