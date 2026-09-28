package si.konferenca.registration.service;

import java.time.Instant;
import java.util.UUID;
import si.konferenca.registration.domain.RegistrationType;

/** Outcome of an accepted registration. */
public record RegistrationResult(UUID registrationId, RegistrationType type, Instant createdAt) {}
