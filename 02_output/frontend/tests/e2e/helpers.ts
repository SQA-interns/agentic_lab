// Helpers shared by the end-to-end journeys.
import { expect, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { attachmentText, messagesTo } from "./mailpit";
import { loadState } from "./stack";

export const uniqueEmail = (prefix: string) => `${prefix}-${randomUUID().slice(0, 12)}@example.si`;

export async function fillExternal(
  page: Page,
  email: string,
  first = "Čedomir",
  last = "Šuštaršič",
) {
  await page.goto("/");
  await page.getByTestId("type-external").check();
  await page.getByTestId("field-firstName").fill(first);
  await page.getByTestId("field-lastName").fill(last);
  await page.getByTestId("field-email").fill(email);
  await page.getByTestId("field-organization").fill("Žalec d.o.o.");
  await page.getByTestId("option-ws-testing").check();
  await page.getByTestId("consent-data-processing").check();
  await page.getByTestId("recaptcha-test").check();
}

export function organizerAuth(): string {
  const state = loadState();
  return `Basic ${Buffer.from(`${state.organizerUsername}:${state.organizerPassword}`).toString("base64")}`;
}

/** The raw JSON copy attached to the single organizer email about `email`. */
export async function organizerCopy(email: string): Promise<{
  type: string;
  participant: Record<string, string>;
}> {
  const organizer = loadState().organizerEmail;
  await expect
    .poll(async () => (await messagesTo(organizer, email)).length, { timeout: 30_000 })
    .toBe(1);
  const [mail] = await messagesTo(organizer, email);
  expect(mail.Attachments).toHaveLength(1);
  return JSON.parse(await attachmentText(mail.ID, mail.Attachments[0].PartID));
}
