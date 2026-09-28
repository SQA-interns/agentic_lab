import { expect, test, type Page } from "@playwright/test";

/*
 * End-to-end tests against the running stack: Vite dev server (baseURL, proxies /api)
 * + backend in reCAPTCHA test mode + PostgreSQL + Mailpit (see docs/test-strategy.md).
 */

const MAILPIT = process.env.E2E_MAILPIT_URL ?? "http://localhost:8025";
const ORGANIZER_USER = process.env.E2E_ORGANIZER_USERNAME ?? "organizer";
const ORGANIZER_PASSWORD =
  process.env.E2E_ORGANIZER_PASSWORD ?? "local-dev-organizer";

function uniqueEmail(prefix: string): string {
  return `${prefix}.${Date.now()}.${Math.floor(Math.random() * 1e6)}@example.si`;
}

async function fill(page: Page, label: string, value: string) {
  await page.getByLabel(new RegExp(`^${label}`)).fill(value);
}

async function acceptAndConfirmHuman(page: Page) {
  await page.getByLabel(/I agree that my personal data/).check();
  await page.getByLabel(/I am not a robot \(test mode\)/).check();
}

interface MailpitMessage {
  ID: string;
  Subject: string;
  To: { Address: string }[];
  Attachments: number;
}

async function messagesTo(
  request: import("@playwright/test").APIRequestContext,
  address: string,
): Promise<MailpitMessage[]> {
  const response = await request.get(
    `${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${address}"`)}`,
  );
  expect(response.ok()).toBeTruthy();
  return ((await response.json()) as { messages: MailpitMessage[] }).messages;
}

test("external participant registers and receives confirmation and email", async ({
  page,
  request,
}) => {
  // AC-001-01, AC-001-06, AC-004-01, AC-006-01, AC-007-01
  const email = uniqueEmail("ana");
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "External participant registration" }),
  ).toBeVisible();

  await fill(page, "First name", "  Ana  ");
  await fill(page, "Last name", "Novak");
  await fill(page, "Email", email);
  await fill(page, "Organization / institution", "Institut Jožef Stefan");
  await page
    .getByLabel("Workshop: Artificial intelligence in practice")
    .check();
  await acceptAndConfirmHuman(page);
  await page.getByRole("button", { name: "Register" }).click();

  await expect(page.getByText("Registration received")).toBeVisible();
  const reference = await page.getByTestId("registration-id").textContent();
  expect(reference).toMatch(/^[0-9a-f-]{36}$/);

  await expect
    .poll(async () => (await messagesTo(request, email)).length)
    .toBe(1);
  const [confirmation] = await messagesTo(request, email);
  expect(confirmation.Subject).toBe("Conference registration confirmed");

  const organizerMails = await messagesTo(
    request,
    "organizers@conference.local",
  );
  const detail = await Promise.all(
    organizerMails.map(async (m) =>
      (await request.get(`${MAILPIT}/api/v1/message/${m.ID}`)).json(),
    ),
  );
  const notification = detail.find((d: { Text: string }) =>
    d.Text.includes(email),
  );
  expect(notification).toBeDefined();
  expect(notification.Subject).toBe("New conference registration (EXTERNAL)");
  expect(notification.Attachments[0].FileName).toBe(
    `registration-${reference}.json`,
  );
});

test("student registers with Slovenian characters", async ({ page }) => {
  // AC-002-01, AC-001-10
  await page.goto("/");
  await page.getByLabel("Student", { exact: true }).check();
  await fill(page, "First name", "Špela");
  await fill(page, "Last name", "Čeh-Žužek");
  await fill(page, "Email", uniqueEmail("spela"));
  await fill(page, "Study institution", "Univerza v Ljubljani");
  await fill(page, "Study programme", "Računalništvo in informatika");
  await fill(page, "Student ID", "63210001");
  await page.getByLabel("Conference dinner").check();
  await acceptAndConfirmHuman(page);
  await page.getByRole("button", { name: "Register" }).click();

  await expect(
    page.getByText("Your student registration has been received successfully."),
  ).toBeVisible();
});

test("invalid submission shows errors and no confirmation", async ({
  page,
}) => {
  // AC-001-03, AC-001-05, AC-001-08, AC-004-02
  await page.goto("/");
  await fill(page, "First name", "Ana");
  await fill(page, "Email", "ana@");
  await page.getByRole("button", { name: "Register" }).click();

  await expect(
    page.getByText("Please correct the highlighted fields."),
  ).toBeVisible();
  await expect(
    page.getByText("Email must be a valid email address."),
  ).toBeVisible();
  await expect(page.getByText("This consent is required.")).toBeVisible();
  await expect(page.getByText("Registration received")).toHaveCount(0);
  await expect(page.getByLabel(/^First name/)).toHaveValue("Ana");
});

test("only active options are offered and consent is not preselected", async ({
  page,
}) => {
  // AC-003-01, AC-003-02, AC-002-10
  await page.goto("/");
  for (const group of ["Workshops", "Events", "Meals", "Other activities"]) {
    await expect(page.getByRole("group", { name: group })).toBeVisible();
  }
  await expect(
    page.getByText("Workshop: Legacy systems (cancelled)"),
  ).toHaveCount(0);
  await expect(
    page.getByLabel(/I agree that my personal data/),
  ).not.toBeChecked();
});

test("export requires organizer credentials", async ({ request }) => {
  // AC-008-01, AC-008-04
  const anonymous = await request.get("/api/organizer/registrations.xlsx");
  expect(anonymous.status()).toBe(401);

  const authorized = await request.get("/api/organizer/registrations.xlsx", {
    headers: {
      Authorization: `Basic ${Buffer.from(`${ORGANIZER_USER}:${ORGANIZER_PASSWORD}`).toString("base64")}`,
    },
  });
  expect(authorized.status()).toBe(200);
  expect(authorized.headers()["content-type"]).toBe(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  );
  const body = await authorized.body();
  expect(body.subarray(0, 2).toString()).toBe("PK"); // XLSX is a ZIP container
});
