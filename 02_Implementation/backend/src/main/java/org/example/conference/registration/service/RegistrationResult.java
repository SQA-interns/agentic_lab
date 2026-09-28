package org.example.conference.registration.service;

import java.time.Instant;
import java.util.UUID;
import org.example.conference.registration.domain.ParticipantType;

/** Outcome of an accepted (or replayed) registration. */
public record RegistrationResult(
    UUID registrationId, ParticipantType participantType, Instant submittedAt, boolean replayed) {}
