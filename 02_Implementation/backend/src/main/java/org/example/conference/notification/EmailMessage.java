package org.example.conference.notification;

import java.util.UUID;

/**
 * An email to be queued. Subject must not contain user data; body is plain text. Attachment is
 * optional (name and UTF-8 content both null or both set).
 */
public record EmailMessage(
    UUID registrationId,
    EmailKind kind,
    String recipient,
    String subject,
    String body,
    String attachmentName,
    String attachmentContent) {}
