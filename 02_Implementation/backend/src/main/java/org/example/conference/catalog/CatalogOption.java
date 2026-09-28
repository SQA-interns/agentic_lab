package org.example.conference.catalog;

/** A configurable conference option with stable identifier and active status. */
public record CatalogOption(String id, String name, boolean active) {}
