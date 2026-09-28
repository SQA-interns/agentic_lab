package si.konferenca.registration.api.dto;

import java.time.Instant;
import java.util.UUID;
import si.konferenca.registration.domain.RegistrationType;

/** Response of an accepted registration. */
public record RegistrationResponse(
    UUID registrationId, RegistrationType registrationType, Instant createdAt) {}
