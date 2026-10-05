// Helpers of the end-to-end tests: they run in the Playwright container on the compose network
// against the running local stack (docs/02_specification.md §12).
import { expect, type APIRequestContext, type Page } from "@playwright/test";

export const MAILPIT_URL = process.env.E2E_MAILPIT_URL ?? "http://mailpit:8025";

export interface FormOption {
  id: string;
  name: string;
  category: string;
  offeredTo: string[];
}

export interface FormConsent {
  id: string;
  text: string;
  mandatory: boolean;
}

export interface FormConfig {
  conferenceName: string;
  options: FormOption[];
  consents: FormConsent[];
  captcha: { mode: string; siteKey?: string };
}

/** An email address no earlier run has used, so the duplicate rule (D-09) never interferes. */
export function uniqueEmail(prefix: string): string {
  return `${prefix}.${Date.now()}.${Math.floor(Math.random() * 1e6)}@example.com`;
}

export async function formConfig(
  request: APIRequestContext,
): Promise<FormConfig> {
  const response = await request.get("/api/form-config");
  expect(response.status()).toBe(200);
  return (await response.json()) as FormConfig;
}

export function optionsFor(
  config: FormConfig,
  type: "external" | "student",
): FormOption[] {
  return config.options.filter((o) => o.offeredTo.includes(type));
}

export async function openForm(page: Page) {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Conference registration" }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Register" })).toBeVisible();
}

export async function giveMandatoryConsentsAndCaptcha(
  page: Page,
  config: FormConfig,
) {
  for (const consent of config.consents.filter((c) => c.mandatory)) {
    await page
      .getByRole("checkbox", { name: consent.text, exact: true })
      .check();
  }
  await page
    .getByRole("checkbox", { name: "I am not a robot (test mode)" })
    .check();
}

/** The registration id shown in the confirmation. */
export async function confirmedRegistrationId(page: Page): Promise<string> {
  const confirmation = page.getByRole("status");
  await expect(
    confirmation.getByRole("heading", { name: "Registration received" }),
  ).toBeVisible({
    timeout: 15000,
  });
  const text = (await confirmation.textContent()) ?? "";
  const match = text.match(
    /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/,
  );
  expect(match, "registration id in the confirmation").not.toBeNull();
  return match![0];
}

export interface MailSummary {
  ID: string;
  To: { Address: string }[];
  Subject: string;
}

export interface MailMessage extends MailSummary {
  Text: string;
  Attachments: { PartID: string; FileName: string; ContentType: string }[];
}

export async function mailMessages(
  request: APIRequestContext,
): Promise<MailSummary[]> {
  const response = await request.get(
    `${MAILPIT_URL}/api/v1/messages?limit=500`,
  );
  expect(response.status()).toBe(200);
  return ((await response.json()) as { messages: MailSummary[] }).messages;
}

export async function mailMessage(
  request: APIRequestContext,
  id: string,
): Promise<MailMessage> {
  const response = await request.get(`${MAILPIT_URL}/api/v1/message/${id}`);
  expect(response.status()).toBe(200);
  return (await response.json()) as MailMessage;
}

export async function mailAttachment(
  request: APIRequestContext,
  messageId: string,
  partId: string,
): Promise<Buffer> {
  const response = await request.get(
    `${MAILPIT_URL}/api/v1/message/${messageId}/part/${partId}`,
  );
  expect(response.status()).toBe(200);
  return response.body();
}

/** Waits for the first message that matches, and returns it in full. */
export async function awaitMail(
  request: APIRequestContext,
  matches: (message: MailMessage) => boolean,
): Promise<MailMessage> {
  let found: MailMessage | undefined;
  await expect
    .poll(
      async () => {
        for (const summary of await mailMessages(request)) {
          const message = await mailMessage(request, summary.ID);
          if (matches(message)) {
            found = message;
            return true;
          }
        }
        return false;
      },
      { timeout: 20000, intervals: [500] },
    )
    .toBe(true);
  return found!;
}

export function organizerAuthorization(): string {
  const user = process.env.ORGANIZER_USERNAME ?? "";
  const password = process.env.ORGANIZER_PASSWORD ?? "";
  return (
    "Basic " + Buffer.from(`${user}:${password}`, "utf8").toString("base64")
  );
}
