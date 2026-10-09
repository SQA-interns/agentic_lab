package si.konferenca.registration.domain;

import java.time.Instant;

/** A consent the participant gave, with the wording shown and the time (SB-14). */
public record GivenConsent(String id, String text, Instant givenAt) {}
