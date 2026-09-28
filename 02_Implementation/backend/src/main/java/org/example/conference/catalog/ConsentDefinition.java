package org.example.conference.catalog;

/** A configured consent statement; wording is supplied by configuration, never by code. */
public record ConsentDefinition(String id, String text, boolean required) {}
