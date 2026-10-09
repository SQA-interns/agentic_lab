import { expect, type Page } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { randomUUID } from "node:crypto";
import { inflateRawSync } from "node:zlib";
import { stack } from "./stack";

/** Selectors and texts from docs/02_contracts/ui-form.json. */
export const ui = {
  type: { EXTERNAL: "type-external", STUDENT: "type-student" },
  field: (name: string) => `field-${name}`,
  error: (name: string) => `error-${name}`,
  category: (category: string) => `category-${category}`,
  option: (id: string) => `option-${id}`,
  consent: (id: string) => `consent-${id}`,
  captchaTestCheckbox: "captcha-test-checkbox",
  submit: "submit",
  formError: "form-error",
  confirmation: "confirmation",
  messages: {
    REQUIRED: "This field is required.",
    ALREADY_REGISTERED: "A registration with this email already exists.",
    CAPTCHA_FAILED: "Please confirm that you are not a robot.",
    GENERAL: "Your registration could not be saved. Please try again later.",
  },
};

export function uniqueEmail(): string {
  return `e2e-${randomUUID().slice(0, 12)}@example.si`;
}

export async function openForm(page: Page, type: "EXTERNAL" | "STUDENT") {
  await page.goto(stack().frontendUrl);
  await expect(page.getByTestId(ui.type[type])).toBeVisible();
  await page.getByTestId(ui.type[type]).click();
  await expect(page.getByTestId(ui.field("firstName"))).toBeVisible();
}

/** Fills the given fields; keys are field names of ui-form.json. */
export async function fill(page: Page, values: Record<string, string>) {
  for (const [name, value] of Object.entries(values)) {
    await page.getByTestId(ui.field(name)).fill(value);
  }
}

export async function tick(page: Page, testId: string) {
  await page.getByTestId(testId).check();
}

export async function confirmNotARobot(page: Page) {
  await tick(page, ui.captchaTestCheckbox);
}

export async function submit(page: Page) {
  await page.getByTestId(ui.submit).click();
}

export const external = (email: string) => ({
  firstName: "Ana",
  lastName: "Novak",
  email,
  organization: "Institut Jožef Stefan",
});

export const student = (email: string) => ({
  firstName: "Luka",
  lastName: "Kranjc",
  email,
  studyInstitution: "Univerza v Mariboru",
  studyProgramme: "Informatika",
  studentId: "E1234567",
});

/** Mailpit messages mentioning the term, waiting until at least `count` exist. */
export async function mails(
  term: string,
  count: number,
): Promise<Array<Record<string, unknown>>> {
  const deadline = Date.now() + 15_000;
  for (;;) {
    const response = await fetch(
      `${stack().mailpitUrl}/api/v1/search?query=${encodeURIComponent(`"${term}"`)}`,
    );
    const body = (await response.json()) as { messages: Array<{ ID: string }> };
    if (body.messages.length >= count || Date.now() > deadline) {
      return Promise.all(
        body.messages.map(async (m) =>
          (await fetch(`${stack().mailpitUrl}/api/v1/message/${m.ID}`)).json(),
        ),
      );
    }
    await new Promise((r) => setTimeout(r, 250));
  }
}

/** The export workbook as raw bytes (the organizer view of the database). */
export async function exportWorkbook(): Promise<{
  status: number;
  bytes: Buffer;
}> {
  const s = stack();
  const auth = Buffer.from(
    `${s.organizerUser}:${s.organizerPassword}`,
  ).toString("base64");
  const response = await fetch(`${s.backendUrl}/api/registrations/export`, {
    headers: { Authorization: `Basic ${auth}` },
  });
  return {
    status: response.status,
    bytes: Buffer.from(await response.arrayBuffer()),
  };
}

/** All text of the workbook: shared strings plus the sheet XML (inline strings). */
export function workbookText(bytes: Buffer): string {
  return zipEntries(bytes)
    .filter(
      (e) =>
        e.name === "xl/sharedStrings.xml" ||
        e.name.startsWith("xl/worksheets/"),
    )
    .map((e) => e.text)
    .join("\n");
}

/** Reads every entry of a ZIP archive through its central directory. */
function zipEntries(zip: Buffer): Array<{ name: string; text: string }> {
  const eocd = zip.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  if (eocd < 0) return [];
  const count = zip.readUInt16LE(eocd + 10);
  let offset = zip.readUInt32LE(eocd + 16);
  const entries: Array<{ name: string; text: string }> = [];
  for (let i = 0; i < count; i++) {
    const method = zip.readUInt16LE(offset + 10);
    const compressed = zip.readUInt32LE(offset + 20);
    const nameLength = zip.readUInt16LE(offset + 28);
    const extraLength = zip.readUInt16LE(offset + 30);
    const commentLength = zip.readUInt16LE(offset + 32);
    const local = zip.readUInt32LE(offset + 42);
    const name = zip.toString("utf-8", offset + 46, offset + 46 + nameLength);
    const dataStart =
      local + 30 + zip.readUInt16LE(local + 26) + zip.readUInt16LE(local + 28);
    const data = zip.subarray(dataStart, dataStart + compressed);
    entries.push({
      name,
      text: (method === 8 ? inflateRawSync(data) : data).toString("utf-8"),
    });
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return entries;
}

/** Contents of all JSON copies written by the backend. */
export function jsonCopies(): string[] {
  const dir = stack().jsonCopyDir;
  return readdirSync(dir)
    .filter((f) => f.endsWith(".json"))
    .map((f) => readFileSync(join(dir, f), "utf-8"));
}
