package si.konferenca.registration.application;

/** What the forms need besides the catalog: conference name and anti-automation mode (AR-07). */
public record FormSettings(String conferenceName, boolean captchaTestMode, String captchaSiteKey) {}
