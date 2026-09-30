package lab.conference.registration;

import java.io.IOException;
import java.util.UUID;
import lab.conference.notifications.AttachmentSource;
import org.springframework.stereotype.Component;

/** Supplies the stored raw JSON as the organizer mail attachment (BR-08). */
@Component
public class RegistrationAttachments implements AttachmentSource {

  private final JsonStore store;

  public RegistrationAttachments(JsonStore store) {
    this.store = store;
  }

  @Override
  public byte[] load(UUID registrationId) throws IOException {
    return store.read(registrationId);
  }
}
