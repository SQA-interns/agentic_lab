package si.konferenca.registration.application;

/** Settings the forms may see; the site key is the only key the frontend gets (AR-07). */
public record PublicSettings(
    String conferenceName, boolean captchaTestMode, String captchaSiteKey) {}
