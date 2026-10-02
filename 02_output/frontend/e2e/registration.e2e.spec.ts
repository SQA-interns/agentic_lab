// End-to-end tests against the running local stack: browser, frontend, backend, database,
// JSON copy, mail catcher and export together.
import { type Page, expect, test } from "@playwright/test";
import {
  ORGANIZER_PASSWORD,
  ORGANIZER_USERNAME,
  attachmentText,
  basicAuthorization,
  mailsOfRegistration,
  recentMails,
  workbookXml,
} from "./support";

const EXPORT_PATH = "/api/registrations/export";
const FIRST_NAME = /^Ime(?![a-zčšž])/i;

function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}@example.org`;
}

async function consentText(page: Page): Promise<string> {
  const reply = await page.request.get("/api/form-config");
  expect(reply.status()).toBe(200);
  return ((await reply.json()) as { consent: { text: string } }).consent.text;
}

async function giveConsentAndPassCaptcha(page: Page): Promise<void> {
  await page.getByRole("checkbox", { name: await consentText(page), exact: true }).check();
  await page.getByRole("checkbox", { name: /^Nisem robot/ }).check();
}

async function exportedWorkbookXml(page: Page): Promise<string> {
  const reply = await page.request.get(EXPORT_PATH, {
    headers: { Authorization: basicAuthorization(ORGANIZER_USERNAME, ORGANIZER_PASSWORD) },
  });
  expect(reply.status()).toBe(200);
  return workbookXml(await reply.body());
}

test("AC-001-01 AC-003-01 AC-004-01 AC-006-01 AC-007-01 AC-007-02 AC-008-01 AC-008-02 AC-008-05 external registration with Slovenian characters reaches the confirmation, both emails, the JSON copy and the export", async ({
  page,
}) => {
  const email = uniqueEmail("ziva.cucnik");
  await page.goto("/");
  await page.getByRole("radio", { name: /^Zunanji udeleženec/ }).check();
  await page.getByRole("textbox", { name: FIRST_NAME }).fill("Živa");
  await page.getByRole("textbox", { name: /^Priimek/ }).fill("Čučnik Šušteršič");
  await page.getByRole("textbox", { name: /^E-pošta/ }).fill(email);
  await page
    .getByRole("textbox", { name: /^Organizacija \/ ustanova/ })
    .fill("Inštitut za računalništvo Žalec");
  await page
    .getByRole("group", { name: "Delavnice" })
    .getByRole("checkbox", { name: "Delavnica: testiranje programske opreme" })
    .check();
  await page
    .getByRole("group", { name: "Dogodki" })
    .getByRole("checkbox", { name: "Slavnostna večerja" })
    .check();
  await giveConsentAndPassCaptcha(page);

  await page.getByRole("button", { name: /^Oddaj prijavo/ }).click();

  await expect(page.getByRole("status")).toContainText("Prijava je sprejeta");

  const { confirmation, notification } = await mailsOfRegistration(page.request, email);
  expect(confirmation.to).toEqual([email]);
  expect(confirmation.html).toBe("");
  for (const text of ["Živa", "Čučnik Šušteršič", "Inštitut za računalništvo Žalec"]) {
    expect(confirmation.text).toContain(text);
    expect(notification.text).toContain(text);
  }
  expect(confirmation.text).toContain("Slavnostna večerja");
  expect(notification.attachments).toHaveLength(1);
  const attachment = notification.attachments[0];
  expect(attachment?.fileName).toMatch(/^registration-[0-9a-f-]{36}\.json$/);
  const copy = JSON.parse(
    await attachmentText(page.request, notification, attachment?.partId ?? ""),
  ) as {
    type: string;
    firstName: string;
    lastName: string;
    email: string;
    organization: string;
    options: { id: string; name: string }[];
  };
  expect(copy.type).toBe("EXTERNAL");
  expect(copy.firstName).toBe("Živa");
  expect(copy.lastName).toBe("Čučnik Šušteršič");
  expect(copy.email).toBe(email);
  expect(copy.organization).toBe("Inštitut za računalništvo Žalec");
  expect(copy.options.map((option) => option.name)).toEqual([
    "Delavnica: testiranje programske opreme",
    "Slavnostna večerja",
  ]);

  const refused = await page.request.get(EXPORT_PATH);
  expect(refused.status()).toBe(401);
  expect(await refused.text()).not.toContain(email);

  const workbook = await exportedWorkbookXml(page);
  for (const text of [
    email,
    "Živa",
    "Čučnik Šušteršič",
    "Inštitut za računalništvo Žalec",
    "Slavnostna večerja",
  ]) {
    expect(workbook).toContain(text);
  }
});

test("AC-002-01 AC-004-01 AC-006-01 AC-008-01 student registration reaches the confirmation, the email and the export", async ({
  page,
}) => {
  const email = uniqueEmail("zan.kosir");
  const studentId = `6321${Math.floor(Math.random() * 9000) + 1000}`;
  await page.goto("/");
  await page.getByRole("radio", { name: /^Študent/ }).check();
  await page.getByRole("textbox", { name: FIRST_NAME }).fill("Žan");
  await page.getByRole("textbox", { name: /^Priimek/ }).fill("Košir");
  await page.getByRole("textbox", { name: /^E-pošta/ }).fill(email);
  await page.getByRole("textbox", { name: /^Izobraževalna ustanova/ }).fill("Univerza v Ljubljani");
  await page
    .getByRole("textbox", { name: /^Študijski program/ })
    .fill("Računalništvo in informatika");
  await page.getByRole("textbox", { name: /^Vpisna številka/ }).fill(studentId);
  await page.getByRole("checkbox", { name: "Kosilo, prvi dan" }).check();
  await giveConsentAndPassCaptcha(page);

  await page.getByRole("button", { name: /^Oddaj prijavo/ }).click();

  await expect(page.getByRole("status")).toContainText("Prijava je sprejeta");
  const { confirmation, notification } = await mailsOfRegistration(page.request, email);
  expect(confirmation.to).toEqual([email]);
  for (const text of ["Žan", "Košir", "Računalništvo in informatika", studentId]) {
    expect(confirmation.text).toContain(text);
    expect(notification.text).toContain(text);
  }
  const workbook = await exportedWorkbookXml(page);
  for (const text of [
    email,
    "Košir",
    "Računalništvo in informatika",
    studentId,
    "Kosilo, prvi dan",
  ]) {
    expect(workbook).toContain(text);
  }
});

test("AC-001-03 AC-004-02 AC-006-02 a registration with an empty required field is not accepted and sends no email", async ({
  page,
}) => {
  const email = uniqueEmail("brez.imena");
  await page.goto("/");
  await page.getByRole("radio", { name: /^Zunanji udeleženec/ }).check();
  await page.getByRole("textbox", { name: /^Priimek/ }).fill("Novak");
  await page.getByRole("textbox", { name: /^E-pošta/ }).fill(email);
  await page.getByRole("textbox", { name: /^Organizacija \/ ustanova/ }).fill("Podjetje Primer");
  await giveConsentAndPassCaptcha(page);

  await page.getByRole("button", { name: /^Oddaj prijavo/ }).click();

  await expect(page.getByRole("textbox", { name: FIRST_NAME })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await expect(page.getByText("To polje je obvezno.")).toBeVisible();
  await expect(page.getByRole("status")).toHaveCount(0);
  await expect(page.getByRole("textbox", { name: /^Priimek/ })).toHaveValue("Novak");
  await page.waitForTimeout(1500);
  const mails = await recentMails(page.request);
  expect(mails.filter((mail) => mail.to.includes(email) || mail.text.includes(email))).toHaveLength(
    0,
  );
});
