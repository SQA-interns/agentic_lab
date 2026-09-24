package org.conference.registration.service;

import java.time.Instant;
import java.util.UUID;
import org.conference.registration.domain.RegistrationType;

/** Outcome of a successfully processed registration. */
public record RegistrationResult(UUID id, RegistrationType type, Instant createdAt) {}
