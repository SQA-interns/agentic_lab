package lab.conference.registration;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.List;

/** Validated ORGANIZER_EMAILS list (configuration, never user input). */
public record OrganizerRecipients(List<String> addresses) {

  public OrganizerRecipients {
    if (addresses == null || addresses.isEmpty()) {
      throw new IllegalStateException("ORGANIZER_EMAILS must list at least one address");
    }
    addresses = addresses.stream().map(String::trim).filter(a -> !a.isEmpty()).toList();
    for (String a : addresses) {
      try {
        new InternetAddress(a, true).validate();
      } catch (AddressException e) {
        throw new IllegalStateException("ORGANIZER_EMAILS contains an invalid address", e);
      }
    }
  }
}
