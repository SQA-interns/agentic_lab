package si.konferenca.registration.web.dto;

import java.util.UUID;
import si.konferenca.registration.domain.RegistrationType;

/** Body of a successful {@code POST /api/registrations}. */
public record RegistrationResponse(UUID registrationId, RegistrationType type) {}
