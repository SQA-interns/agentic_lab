package si.konferenca.registration.application;

import java.util.List;

/**
 * A plain-text email; the attachment is optional ({@code attachmentName} null when absent).
 *
 * @param to recipients
 * @param subject subject, never containing user input (SR-05)
 * @param text plain-text body
 * @param attachmentName file name of the JSON attachment, or null
 * @param attachment attachment bytes, or null
 */
public record OutgoingMail(
    List<String> to, String subject, String text, String attachmentName, byte[] attachment) {

  public OutgoingMail {
    to = List.copyOf(to);
    attachment = attachment == null ? null : attachment.clone();
  }

  @Override
  public byte[] attachment() {
    return attachment == null ? null : attachment.clone();
  }
}
