package org.example.conference.registration.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Acceptance response. {@code emailStatus} is always PENDING: email is queued, never claimed as
 * delivered at acceptance time (AR-05).
 */
public record RegistrationResponse(
    UUID registrationId,
    String participantType,
    Instant submittedAt,
    String emailStatus,
    boolean replayed) {}
