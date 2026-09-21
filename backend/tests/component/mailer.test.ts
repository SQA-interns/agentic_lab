/**
 * Component tests — the Nodemailer adapter (specification § 7).
 *
 * Level justification: every other test replaces this adapter with a recording port, so
 * without this suite nothing would check that the port contract is actually translated
 * into a Nodemailer message — in particular the sender, the recipient list, the reply-to
 * and the JSON attachment. The `json` transport composes a real message without needing
 * an SMTP server, so the assertions are made on what would have been sent.
 */
import { describe, expect, it } from 'vitest';

import { buildTestConfig } from '../helpers/testApplication.js';
import { NodemailerMailAdapter } from '../../src/infrastructure/mail/mailer.js';

function adapter(): NodemailerMailAdapter {
  return new NodemailerMailAdapter(buildTestConfig());
}

describe('NodemailerMailAdapter', () => {
  it('sends a message with the configured sender and the requested recipients', async () => {
    const mailer = adapter();
    await mailer.send({
      to: ['ana@example.org'],
      subject: 'Confirmation',
      text: 'text body',
      html: '<p>html body</p>',
    });

    expect(mailer.captured).toHaveLength(1);
    expect(mailer.captured[0]).toMatchObject({
      from: 'conference@example.org',
      to: ['ana@example.org'],
      subject: 'Confirmation',
    });
    mailer.close();
  });

  it('carries the reply-to address when one is given (AC-007-07)', async () => {
    const mailer = adapter();
    await mailer.send({
      to: ['organizer@example.org'],
      subject: 'New registration',
      text: 't',
      html: '<p>t</p>',
      replyTo: 'ana@example.org',
    });

    expect(mailer.captured[0]?.replyTo).toBe('ana@example.org');
    mailer.close();
  });

  it('carries a JSON attachment unchanged (AC-007-03)', async () => {
    const mailer = adapter();
    const content = Buffer.from('{"reference":"REG-20260921-ABCDEFGHJK"}', 'utf8');
    await mailer.send({
      to: ['organizer@example.org'],
      subject: 'New registration',
      text: 't',
      html: '<p>t</p>',
      attachments: [
        { filename: 'REG-20260921-ABCDEFGHJK.json', content, contentType: 'application/json' },
      ],
    });

    const attachment = mailer.captured[0]?.attachments?.[0];
    expect(attachment?.filename).toBe('REG-20260921-ABCDEFGHJK.json');
    expect(attachment?.contentType).toBe('application/json');
    expect(attachment?.content.equals(content)).toBe(true);
    mailer.close();
  });

  it('sends several messages independently, so one registration produces two separate emails', async () => {
    const mailer = adapter();
    await mailer.send({ to: ['ana@example.org'], subject: 'a', text: 'a', html: '<p>a</p>' });
    await mailer.send({ to: ['organizer@example.org'], subject: 'b', text: 'b', html: '<p>b</p>' });

    expect(mailer.captured.map((message) => message.to)).toEqual([
      ['ana@example.org'],
      ['organizer@example.org'],
    ]);
    mailer.close();
  });

  it('records a send timestamp for each captured message', async () => {
    const mailer = adapter();
    await mailer.send({ to: ['ana@example.org'], subject: 'a', text: 'a', html: '<p>a</p>' });
    expect(mailer.captured[0]?.sentAt).toMatch(/^\d{4}-\d{2}-\d{2}T/u);
    mailer.close();
  });
});
