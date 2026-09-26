import { expect, request, test, type Page } from "@playwright/test";

// Requires the Docker Compose stack (backend with RECAPTCHA_TEST_MODE=true, Mailpit on :8025)
// and the Vite dev server on :5173, see docs/test-strategy.md.
const MAILPIT = process.env.MAILPIT_URL ?? "http://localhost:8025";
const ORGANIZER_USER = process.env.ORGANIZER_USERNAME ?? "organizer";
const ORGANIZER_PASSWORD =
  process.env.ORGANIZER_PASSWORD ?? "local-dev-organizer";

function uniqueEmail(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.si`;
}

async function messagesTo(address: string) {
  const mailpit = await request.newContext({ baseURL: MAILPIT });
  const response = await mailpit.get(
    `/api/v1/search?query=${encodeURIComponent(`to:"${address}"`)}`,
  );
  expect(response.ok()).toBeTruthy();
  const body = (await response.json()) as {
    messages: { Subject: string; ID: string }[];
  };
  await mailpit.dispose();
  return body.messages;
}

async function organizerMessageFor(address: string) {
  const mailpit = await request.newContext({ baseURL: MAILPIT });
  const response = await mailpit.get(
    `/api/v1/search?query=${encodeURIComponent(`to:"organizer@conference.local" ${address}`)}`,
  );
  const body = (await response.json()) as {
    messages: { Subject: string; ID: string; Attachments: number }[];
  };
  await mailpit.dispose();
  return body.messages;
}

async function fillCommon(page: Page, email: string) {
  await page.getByLabel(/^First name/).fill("Žiga");
  await page.getByLabel(/^Last name/).fill("Šušteršič");
  await page.getByLabel(/^Email/).fill(email);
}

async function acceptAndSubmit(page: Page) {
  await page.getByLabel(/I agree to the processing/).check();
  await page.getByLabel(/I am not a robot/).check();
  await page.getByRole("button", { name: "Register" }).click();
}

test.describe("conference registration", () => {
  test("external participant registers with options and receives confirmation", async ({
    page,
  }) => {
    const email = uniqueEmail("external");
    await page.goto("/");
    await expect(
      page.getByLabel(/I agree to the processing/),
    ).not.toBeChecked();
    await fillCommon(page, email);
    await page
      .getByLabel(/^Organization \/ institution/)
      .fill("Univerza v Ljubljani — FRI");
    await page.getByLabel("Workshop: Software testing").check();
    await page.getByLabel("Lunch", { exact: true }).check();
    await acceptAndSubmit(page);

    await expect(
      page.getByRole("heading", { name: "Registration received" }),
    ).toBeVisible();
    await expect(page.getByText("External participant")).toBeVisible();

    await expect
      .poll(async () => (await messagesTo(email)).map((m) => m.Subject))
      .toContain("Conference registration confirmation");
    await expect
      .poll(async () => (await organizerMessageFor(email)).length)
      .toBeGreaterThan(0);
    const [organizerMail] = await organizerMessageFor(email);
    expect(organizerMail.Subject).toBe(
      "New conference registration (External participant)",
    );
    expect(organizerMail.Attachments).toBe(1);
  });

  test("student registers and receives confirmation", async ({ page }) => {
    const email = uniqueEmail("student");
    await page.goto("/");
    await page.getByLabel("Student", { exact: true }).check();
    await fillCommon(page, email);
    await page
      .getByLabel(/^Study institution/)
      .fill("Fakulteta za računalništvo");
    await page
      .getByLabel(/^Study programme/)
      .fill("Računalništvo in informatika");
    await page.getByLabel(/^Student ID/).fill("63219999");
    await page.getByLabel("Conference dinner").check();
    await acceptAndSubmit(page);

    await expect(
      page.getByRole("heading", { name: "Registration received" }),
    ).toBeVisible();
    await expect(page.getByText("Student", { exact: true })).toBeVisible();
    await expect
      .poll(async () => (await messagesTo(email)).length)
      .toBeGreaterThan(0);
  });

  test("invalid input is rejected without confirmation", async ({ page }) => {
    await page.goto("/");
    await page.getByLabel(/^Email/).fill("not-an-email");
    await page.getByRole("button", { name: "Register" }).click();
    await expect(page.getByText("Enter a valid email address.")).toBeVisible();
    await expect(page.getByText("This consent is required.")).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Registration received" }),
    ).toHaveCount(0);
  });

  test("inactive options are not offered", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByLabel("Workshop: Software testing")).toBeVisible();
    await expect(page.getByText("Workshop: Archived session")).toHaveCount(0);
  });
});

test.describe("organizer export", () => {
  test("is refused without organizer credentials", async ({ request: api }) => {
    const response = await api.get("/api/admin/registrations/export");
    expect(response.status()).toBe(401);
  });

  test("returns an Excel workbook for the organizer", async ({
    playwright,
    baseURL,
  }) => {
    const organizer = await playwright.request.newContext({
      baseURL,
      httpCredentials: {
        username: ORGANIZER_USER,
        password: ORGANIZER_PASSWORD,
      },
    });
    const response = await organizer.get("/api/admin/registrations/export");
    expect(response.status()).toBe(200);
    expect(response.headers()["content-type"]).toContain(
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    );
    const bytes = await response.body();
    expect(bytes.subarray(0, 2).toString()).toBe("PK");
    await organizer.dispose();
  });
});
