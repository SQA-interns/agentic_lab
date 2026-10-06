import { expect, type APIRequestContext, type Page } from "@playwright/test";

export const MAILPIT_URL = process.env.MAILPIT_URL ?? "http://127.0.0.1:8026";
export const CONSENT_TEXT =
  "I agree that the organizers process the personal data I enter in this form to organise the conference and my participation in it.";

export function organizer() {
  const username = process.env.ORGANIZER_USERNAME;
  const password = process.env.ORGANIZER_PASSWORD;
  const emails = (process.env.ORGANIZER_EMAILS ?? "")
    .split(",")
    .map((e) => e.trim())
    .filter(Boolean);
  if (!username || !password || emails.length === 0) {
    throw new Error("ORGANIZER_USERNAME, ORGANIZER_PASSWORD and ORGANIZER_EMAILS must be set");
  }
  return { username, password, emails };
}

export function uniqueEmail(prefix: string): string {
  return `${prefix}.${Date.now()}.${Math.floor(Math.random() * 1e6)}@example.com`;
}

export async function openForm(page: Page) {
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Register" })).toBeVisible();
}

export async function chooseType(page: Page, label: "External participant" | "Student") {
  await page
    .getByRole("radiogroup", { name: "Registration type" })
    .getByRole("radio", { name: label })
    .check();
}

export async function confirmAndSubmit(page: Page) {
  await page.getByRole("checkbox", { name: CONSENT_TEXT }).check();
  await page.getByRole("checkbox", { name: "I am not a robot (test mode)" }).check();
  await page.getByRole("button", { name: "Register" }).click();
}

export interface External {
  firstName: string;
  lastName: string;
  email: string;
  organization: string;
}

export async function registerExternal(page: Page, person: External, options: string[] = []) {
  await openForm(page);
  await chooseType(page, "External participant");
  await page.getByLabel("First name").fill(person.firstName);
  await page.getByLabel("Last name").fill(person.lastName);
  await page.getByLabel("Email").fill(person.email);
  await page.getByLabel("Organization / institution").fill(person.organization);
  for (const option of options) {
    await page.getByRole("checkbox", { name: option }).check();
  }
  await confirmAndSubmit(page);
}

export async function expectConfirmation(page: Page, firstName: string, email: string) {
  const status = page.getByRole("status");
  await expect(status.getByRole("heading", { name: "Registration received" })).toBeVisible();
  await expect(status).toContainText(
    `Thank you, ${firstName}. A confirmation email is on its way to ${email}.`,
  );
}

interface MailSummary {
  ID: string;
  To: { Address: string }[];
  Subject: string;
  Attachments: number;
}

/** Waits for a message to the address whose content contains the text (Mailpit search API). */
export async function awaitMail(request: APIRequestContext, to: string, text: string) {
  const query = encodeURIComponent(`to:"${to}" "${text}"`);
  let found: MailSummary | undefined;
  await expect
    .poll(
      async () => {
        const response = await request.get(`${MAILPIT_URL}/api/v1/search?query=${query}`);
        const body = (await response.json()) as { messages: MailSummary[] };
        found = body.messages[0];
        return found !== undefined;
      },
      { timeout: 20_000 },
    )
    .toBe(true);
  const response = await request.get(`${MAILPIT_URL}/api/v1/message/${found!.ID}`);
  return (await response.json()) as {
    ID: string;
    To: { Address: string }[];
    Subject: string;
    Text: string;
    Attachments: { PartID: string; FileName: string; ContentType: string }[];
  };
}
