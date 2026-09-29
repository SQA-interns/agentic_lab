import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { inflateRawSync } from 'node:zlib';

/** Settings for the end-to-end tests: environment first, then the repository-root .env. */
function readDotEnv(): Record<string, string> {
  try {
    const text = readFileSync(resolve(process.cwd(), '../../.env'), 'utf8');
    const values: Record<string, string> = {};
    for (const line of text.split(/\r?\n/)) {
      const m = /^([A-Z0-9_]+)=(.*)$/.exec(line.trim());
      if (m) values[m[1]] = m[2];
    }
    return values;
  } catch {
    return {};
  }
}

const dotEnv = readDotEnv();

export function setting(name: string, fallback = ''): string {
  return process.env[name] ?? dotEnv[name] ?? fallback;
}

export const mailpitUrl = setting('E2E_MAILPIT_URL', 'http://127.0.0.1:8025');

export function uniqueEmail(): string {
  return `e2e-${Date.now()}-${Math.random().toString(16).slice(2, 8)}@example.si`;
}

type MailpitMessage = {
  ID: string;
  To: { Address: string }[];
  Subject: string;
  Text: string;
  Attachments: { PartID: string; FileName: string }[];
};

/** Waits until Mailpit holds a message to the address whose text contains the marker. */
export async function awaitMail(address: string, marker: string): Promise<MailpitMessage> {
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    const q = encodeURIComponent(`to:"${address}"`);
    const list = await (await fetch(`${mailpitUrl}/api/v1/search?limit=100&query=${q}`)).json();
    for (const summary of list.messages ?? []) {
      const m: MailpitMessage = await (
        await fetch(`${mailpitUrl}/api/v1/message/${summary.ID}`)
      ).json();
      if (m.Text.includes(marker)) return m;
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`no email to ${address} containing ${marker}`);
}

export async function attachment(messageId: string, partId: string): Promise<string> {
  const r = await fetch(`${mailpitUrl}/api/v1/message/${messageId}/part/${partId}`);
  return Buffer.from(await r.arrayBuffer()).toString('utf8');
}

/** Returns the text of every entry of a zip archive (an .xlsx workbook) whose name matches. */
export function unzipText(zip: Buffer, name: RegExp): string {
  const eocd = zip.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  const entries = zip.readUInt16LE(eocd + 10);
  let offset = zip.readUInt32LE(eocd + 16);
  let text = '';
  for (let i = 0; i < entries; i++) {
    const method = zip.readUInt16LE(offset + 10);
    const compressed = zip.readUInt32LE(offset + 20);
    const nameLength = zip.readUInt16LE(offset + 28);
    const extraLength = zip.readUInt16LE(offset + 30);
    const commentLength = zip.readUInt16LE(offset + 32);
    const local = zip.readUInt32LE(offset + 42);
    const fileName = zip.toString('utf8', offset + 46, offset + 46 + nameLength);
    if (name.test(fileName)) {
      const localName = zip.readUInt16LE(local + 26);
      const localExtra = zip.readUInt16LE(local + 28);
      const start = local + 30 + localName + localExtra;
      const data = zip.subarray(start, start + compressed);
      text += (method === 8 ? inflateRawSync(data) : data).toString('utf8');
    }
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return text;
}
