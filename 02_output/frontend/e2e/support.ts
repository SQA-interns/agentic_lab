// Helpers of the end-to-end tests: the mail catcher API, the organizer export and a reader for
// the exported workbook. Everything is reached over HTTP, as a participant or organizer would.
import { inflateRawSync } from "node:zlib";
import type { APIRequestContext } from "@playwright/test";

export const MAILPIT_URL = process.env.E2E_MAILPIT_URL ?? "http://127.0.0.1:8025";
export const ORGANIZER_USERNAME = process.env.E2E_ORGANIZER_USERNAME ?? "";
export const ORGANIZER_PASSWORD = process.env.E2E_ORGANIZER_PASSWORD ?? "";

export interface CaughtMail {
  id: string;
  to: string[];
  subject: string;
  text: string;
  html: string;
  attachments: { partId: string; fileName: string; contentType: string }[];
}

interface MailpitAddress {
  Address: string;
}

interface MailpitMessage {
  ID: string;
  To: MailpitAddress[] | null;
  Subject: string;
  Text: string;
  HTML: string;
  Attachments: { PartID: string; FileName: string; ContentType: string }[] | null;
}

/** The most recent caught messages, newest first. */
export async function recentMails(request: APIRequestContext): Promise<CaughtMail[]> {
  const list = await request.get(`${MAILPIT_URL}/api/v1/messages?limit=50`);
  const summaries = ((await list.json()) as { messages: { ID: string }[] }).messages;
  const mails: CaughtMail[] = [];
  for (const summary of summaries) {
    const reply = await request.get(`${MAILPIT_URL}/api/v1/message/${summary.ID}`);
    const message = (await reply.json()) as MailpitMessage;
    mails.push({
      id: message.ID,
      to: (message.To ?? []).map((recipient) => recipient.Address),
      subject: message.Subject,
      text: message.Text,
      html: message.HTML,
      attachments: (message.Attachments ?? []).map((attachment) => ({
        partId: attachment.PartID,
        fileName: attachment.FileName,
        contentType: attachment.ContentType,
      })),
    });
  }
  return mails;
}

/** Waits for the participant confirmation and the organizer notification of one registration. */
export async function mailsOfRegistration(
  request: APIRequestContext,
  participantEmail: string,
): Promise<{ confirmation: CaughtMail; notification: CaughtMail }> {
  const deadline = Date.now() + 20_000;
  for (;;) {
    const mails = await recentMails(request);
    const confirmation = mails.find(
      (mail) => mail.to.includes(participantEmail) && mail.subject.startsWith("Potrditev prijave:"),
    );
    const notification = mails.find(
      (mail) => mail.subject.startsWith("Nova prijava:") && mail.text.includes(participantEmail),
    );
    if (confirmation && notification) {
      return { confirmation, notification };
    }
    if (Date.now() > deadline) {
      throw new Error(
        `emails of ${participantEmail} did not arrive: confirmation=${Boolean(confirmation)} notification=${Boolean(notification)}`,
      );
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
}

export async function attachmentText(
  request: APIRequestContext,
  mail: CaughtMail,
  partId: string,
): Promise<string> {
  const reply = await request.get(`${MAILPIT_URL}/api/v1/message/${mail.id}/part/${partId}`);
  return (await reply.body()).toString("utf8");
}

export function basicAuthorization(username: string, password: string): string {
  return `Basic ${Buffer.from(`${username}:${password}`, "utf8").toString("base64")}`;
}

/** The text of every XML part of a workbook (an .xlsx file is a ZIP archive of XML parts). */
export function workbookXml(workbook: Buffer): string {
  let end = workbook.length - 22;
  while (end >= 0 && workbook.readUInt32LE(end) !== 0x06054b50) {
    end -= 1;
  }
  if (end < 0) {
    throw new Error("the export is not a ZIP archive");
  }
  const entries = workbook.readUInt16LE(end + 10);
  let offset = workbook.readUInt32LE(end + 16);
  const parts: string[] = [];
  for (let index = 0; index < entries; index += 1) {
    if (workbook.readUInt32LE(offset) !== 0x02014b50) {
      throw new Error("the export has a damaged ZIP directory");
    }
    const method = workbook.readUInt16LE(offset + 10);
    const compressedSize = workbook.readUInt32LE(offset + 20);
    const nameLength = workbook.readUInt16LE(offset + 28);
    const extraLength = workbook.readUInt16LE(offset + 30);
    const commentLength = workbook.readUInt16LE(offset + 32);
    const localOffset = workbook.readUInt32LE(offset + 42);
    const name = workbook.toString("utf8", offset + 46, offset + 46 + nameLength);
    if (name.endsWith(".xml")) {
      const dataStart =
        localOffset +
        30 +
        workbook.readUInt16LE(localOffset + 26) +
        workbook.readUInt16LE(localOffset + 28);
      const data = workbook.subarray(dataStart, dataStart + compressedSize);
      parts.push((method === 0 ? data : inflateRawSync(data)).toString("utf8"));
    }
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return parts.join("\n");
}
