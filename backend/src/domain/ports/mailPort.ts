/**
 * Outbound mail port (specification § 2.2).
 *
 * The port lives in the domain layer because both sides of it point here: the use case
 * depends on it to send, and the Nodemailer adapter depends on it to implement. Putting
 * it in the application layer would force infrastructure to depend inwards on
 * application, which the layering rule forbids.
 *
 * Declaring the port at all — rather than importing the Nodemailer type into the use
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
