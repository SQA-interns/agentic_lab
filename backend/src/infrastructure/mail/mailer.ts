/**
 * Nodemailer-backed mail adapter (specification § 7).
 *
 * Two transports are supported: `smtp` for real delivery and `json` for local
 * development and tests, where the composed message is captured instead of sent. The
 * environment schema refuses `json` in production, so a production deployment cannot
 * silently stop delivering mail.
 */
import nodemailer, { type Transporter } from 'nodemailer';

import type { MailPort, OutgoingMail } from '../../domain/ports/mailPort.js';
import type { AppConfig } from '../../config/env.js';

export interface CapturedMail extends OutgoingMail {
  readonly from: string;
  readonly sentAt: string;
}

export class NodemailerMailAdapter implements MailPort {
  private readonly transporter: Transporter;
  private readonly from: string;
  /** Populated only by the `json` transport; the capture buffer used by tests. */
  readonly captured: CapturedMail[] = [];
  private readonly capturing: boolean;

  constructor(config: AppConfig) {
    this.from = config.mail.from;
    this.capturing = config.mail.transport === 'json';
    this.transporter =
      config.mail.transport === 'smtp'
        ? nodemailer.createTransport({
            host: config.mail.host,
            port: config.mail.port,
            secure: config.mail.secure,
            ...(config.mail.user
              ? { auth: { user: config.mail.user, pass: config.mail.pass ?? '' } }
              : {}),
          })
        : nodemailer.createTransport({ jsonTransport: true });
  }

  async send(message: OutgoingMail): Promise<void> {
    await this.transporter.sendMail({
      from: this.from,
      to: [...message.to],
      subject: message.subject,
      text: message.text,
      html: message.html,
      ...(message.replyTo ? { replyTo: message.replyTo } : {}),
      ...(message.attachments
        ? {
            attachments: message.attachments.map((attachment) => ({
              filename: attachment.filename,
              content: attachment.content,
              contentType: attachment.contentType,
            })),
          }
        : {}),
    });

    if (this.capturing) {
      this.captured.push({ ...message, from: this.from, sentAt: new Date().toISOString() });
    }
  }

  close(): void {
    this.transporter.close();
  }
}
