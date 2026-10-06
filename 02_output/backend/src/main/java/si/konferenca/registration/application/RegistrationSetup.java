package si.konferenca.registration.application;

import java.util.List;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Consent;

/** What the forms need: conference name, consent, anti-automation settings, active options. */
public record RegistrationSetup(
    String conferenceName,
    Consent consent,
    boolean captchaTestMode,
    String captchaSiteKey,
    List<ConferenceOption> options) {}
