package si.konferenca.registration.domain;

import java.time.Instant;

/** A consent with the wording shown and the time it was given (SB-14). */
public record GivenConsent(String id, String text, Instant givenAt) {}
