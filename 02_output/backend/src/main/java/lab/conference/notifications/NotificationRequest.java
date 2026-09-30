package lab.conference.notifications;

import java.util.UUID;

/**
 * A notification intent. Subject and body are composed by the caller; the attachment, if any, is
 * loaded from {@link AttachmentSource} at send time and must match {@code attachmentSha256}.
 */
public record NotificationRequest(
    UUID registrationId,
    NotificationKind kind,
    String recipient,
    String subject,
    String body,
    String attachmentName,
    String attachmentSha256) {}
