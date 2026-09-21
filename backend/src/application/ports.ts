/**
 * Ports used by the application layer (specification § 2.2).
 *
 * Declaring the mail port here — rather than importing the Nodemailer type into the use
 * case — keeps the application layer free of transport detail and lets tests capture
 * messages without an SMTP server.
 */

export interface MailAttachment {
  readonly filename: string;
  readonly content: Buffer;
  readonly contentType: string;
}

export interface OutgoingMail {
  readonly to: readonly string[];
  readonly subject: string;
  readonly text: string;
  readonly html: string;
  readonly replyTo?: string;
  readonly attachments?: readonly MailAttachment[];
}

export interface MailPort {
  send(message: OutgoingMail): Promise<void>;
}
