import { expect, type APIRequestContext, type Page } from "@playwright/test";

/** Access to the local stack's Mailpit and organizer settings from the end-to-end tests. */

export const mailpitUrl = process.env["E2E_MAILPIT_URL"] ?? "http://127.0.0.1:8025";

export function organizer(): { username: string; password: string; emails: string[] } {
  const username = process.env["ORGANIZER_USERNAME"];
  const password = process.env["ORGANIZER_PASSWORD"];
  const emails = process.env["ORGANIZER_EMAILS"];
  if (!username || !password || !emails) {
    throw new Error("ORGANIZER_USERNAME, ORGANIZER_PASSWORD and ORGANIZER_EMAILS must be set");
  }
  return {
    username,
    password,
    emails: emails
      .split(",")
      .map((address) => address.trim())
      .filter((address) => address !== ""),
  };
}

export function basicAuth(username: string, password: string): string {
  return "Basic " + Buffer.from(`${username}:${password}`, "utf8").toString("base64");
}

export function uniqueEmail(prefix: string): string {
  return `${prefix}.${Date.now()}.${Math.floor(Math.random() * 1e6)}@example.si`;
}

export interface MailMessage {
  ID: string;
  Subject: string;
  Text: string;
  To: { Address: string }[];
  Attachments: { PartID: string; FileName: string; ContentType: string }[];
}

/** Waits for a message to {@code address} whose text contains {@code text}, and returns it. */
export async function awaitMessage(
  request: APIRequestContext,
  address: string,
  text: string,
): Promise<MailMessage> {
  let found: MailMessage | undefined;
  await expect
    .poll(
      async () => {
        const list = await (await request.get(`${mailpitUrl}/api/v1/messages?limit=200`)).json();
        for (const summary of list.messages as { ID: string; To: { Address: string }[] }[]) {
          if (!summary.To.some((to) => to.Address.toLowerCase() === address.toLowerCase())) {
            continue;
          }
          const message = (await (
            await request.get(`${mailpitUrl}/api/v1/message/${summary.ID}`)
          ).json()) as MailMessage;
          if (message.Text.includes(text)) {
            found = message;
            return true;
          }
        }
        return false;
      },
      { timeout: 15_000 },
    )
    .toBe(true);
  return found!;
}

/** Fills the page's form with a valid registration of the given type. */
export async function fillRegistration(
  page: Page,
  type: "EXTERNAL" | "STUDENT",
  fields: Record<string, string>,
): Promise<void> {
  await page.getByTestId(type === "EXTERNAL" ? "type-external" : "type-student").check();
  for (const [field, value] of Object.entries(fields)) {
    await page.getByTestId(`field-${field}`).fill(value);
  }
  await page.getByTestId("consent-data-processing").check();
  await page.getByTestId("captcha-test").check();
}

export const UUID_PATTERN = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/;
