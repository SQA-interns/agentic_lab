package lab.conference.notifications;

import java.io.IOException;
import java.util.UUID;

/** Provides the stored attachment bytes for a registration (implemented by registration). */
public interface AttachmentSource {

  byte[] load(UUID registrationId) throws IOException;
}
