import { expect, type APIRequestContext, type Page } from "@playwright/test";

// Shared helpers for end-to-end tests against the local stack (docker compose, test mode on).
// UI element names come from docs/02_contracts/ui.md; API shapes from openapi.json.

export const MAILPIT_URL = process.env.E2E_MAILPIT_URL ?? "http://127.0.0.1:8025";
export const TEST_MODE_TOKEN = "test-mode-pass";

export type ApiOption = { id: string; name: string; category: string };
export type ApiConsent = { id: string; text: string; required: boolean };

export function uniqueEmail(prefix = "e2e"): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@participants.test`;
}

export async function fetchOptions(
  request: APIRequestContext,
): Promise<{ options: ApiOption[]; consents: ApiConsent[] }> {
  const response = await request.get("/api/options");
  expect(response.status()).toBe(200);
  return response.json();
}

export async function openForm(page: Page): Promise<void> {
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Register" })).toBeVisible();
}

export async function fillExternal(
  page: Page,
  values: { firstName: string; lastName: string; email: string; organization: string },
): Promise<void> {
  await page.getByLabel("External participant", { exact: true }).check();
  await page.getByLabel("First name", { exact: true }).fill(values.firstName);
  await page.getByLabel("Last name", { exact: true }).fill(values.lastName);
  await page.getByLabel("Email", { exact: true }).fill(values.email);
  await page.getByLabel("Organization / institution", { exact: true }).fill(values.organization);
}

export async function checkRequiredConsents(page: Page, consents: ApiConsent[]): Promise<void> {
  for (const c of consents.filter((x) => x.required)) {
    await page.getByLabel(c.text, { exact: false }).check();
  }
}

type MailpitSummary = { ID: string; Subject: string };
export type MailpitMessage = {
  ID: string;
  Subject: string;
  Text: string;
  Attachments: { PartID: string; FileName: string; ContentType: string }[];
};

export async function waitForMessages(query: string, expected = 1): Promise<MailpitMessage[]> {
  for (let i = 0; i < 60; i++) {
    const r = await fetch(`${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(query)}`);
    const body = (await r.json()) as { messages: MailpitSummary[] };
    if (body.messages.length >= expected) {
      return Promise.all(
        body.messages.map(async (m) => {
          const full = await fetch(`${MAILPIT_URL}/api/v1/message/${m.ID}`);
          return (await full.json()) as MailpitMessage;
        }),
      );
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`no email for ${query}`);
}

export async function mailpitPart(messageId: string, partId: string): Promise<string> {
  const r = await fetch(`${MAILPIT_URL}/api/v1/message/${messageId}/part/${partId}`);
  return r.text();
}

// Reads all shared strings of an .xlsx workbook (a zip file) without extra dependencies.
export async function xlsxStrings(bytes: Uint8Array): Promise<string[]> {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let eocd = bytes.length - 22;
  while (eocd >= 0 && view.getUint32(eocd, true) !== 0x06054b50) eocd--;
  const entries = view.getUint16(eocd + 10, true);
  let p = view.getUint32(eocd + 16, true);
  const decoder = new TextDecoder("utf-8");
  for (let i = 0; i < entries; i++) {
    const method = view.getUint16(p + 10, true);
    const compressedSize = view.getUint32(p + 20, true);
    const nameLength = view.getUint16(p + 28, true);
    const extraLength = view.getUint16(p + 30, true);
    const commentLength = view.getUint16(p + 32, true);
    const localOffset = view.getUint32(p + 42, true);
    const name = decoder.decode(bytes.subarray(p + 46, p + 46 + nameLength));
    if (name === "xl/sharedStrings.xml") {
      const localName = view.getUint16(localOffset + 26, true);
      const localExtra = view.getUint16(localOffset + 28, true);
      const start = localOffset + 30 + localName + localExtra;
      const data = bytes.slice(start, start + compressedSize);
      const xml =
        method === 0
          ? decoder.decode(data)
          : await new Response(
              new Blob([data]).stream().pipeThrough(new DecompressionStream("deflate-raw")),
            ).text();
      return [...xml.matchAll(/<t[^>]*>([^<]*)<\/t>/g)].map((m) =>
        m[1]
          .replace(/&lt;/g, "<")
          .replace(/&gt;/g, ">")
          .replace(/&quot;/g, '"')
          .replace(/&amp;/g, "&"),
      );
    }
    p += 46 + nameLength + extraLength + commentLength;
  }
  return [];
}
