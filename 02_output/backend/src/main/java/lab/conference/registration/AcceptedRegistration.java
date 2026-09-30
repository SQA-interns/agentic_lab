package lab.conference.registration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only view of an accepted registration for other modules (export). */
public record AcceptedRegistration(
    UUID registrationId,
    UUID clientRequestId,
    String formType,
    Instant acceptedAt,
    Map<String, String> participant,
    Map<String, List<String>> selectionNames,
    Boolean consentGiven) {}
