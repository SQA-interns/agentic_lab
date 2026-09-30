package si.konferenca.registration.application;

import java.util.List;

/**
 * Settings the use cases need, bound from configuration by the config package (ES-01).
 *
 * @param conferenceName name shown in emails and the client
 * @param organizerEmails organizer notification recipients
 * @param recaptchaSiteKey public reCAPTCHA site key (AR-07)
 * @param mailMaxAttempts delivery attempts before an email is abandoned (D-11)
 */
public record ConferenceSettings(
    String conferenceName,
    List<String> organizerEmails,
    String recaptchaSiteKey,
    int mailMaxAttempts) {

  public ConferenceSettings {
    organizerEmails = List.copyOf(organizerEmails);
  }
}
