package lab.conference.registration;

import java.time.Instant;
import java.util.UUID;

/** Outcome of an accepted (or replayed) submission. */
public record AcceptanceResult(
    UUID registrationId,
    UUID clientRequestId,
    FormType formType,
    Instant acceptedAt,
    boolean replay) {}
