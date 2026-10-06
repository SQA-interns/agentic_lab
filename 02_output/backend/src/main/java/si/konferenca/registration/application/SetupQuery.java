package si.konferenca.registration.application;

import si.konferenca.registration.domain.OptionCatalog;

/** Builds the form setup from configuration (AR-04, AR-07). */
public class SetupQuery {

  private final String conferenceName;
  private final OptionCatalog catalog;
  private final CaptchaVerifier captcha;

  public SetupQuery(String conferenceName, OptionCatalog catalog, CaptchaVerifier captcha) {
    this.conferenceName = conferenceName;
    this.catalog = catalog;
    this.captcha = captcha;
  }

  public RegistrationSetup setup() {
    return new RegistrationSetup(
        conferenceName,
        catalog.consent(),
        captcha.testMode(),
        captcha.testMode() ? "" : captcha.siteKey(),
        catalog.activeOptions());
  }
}
