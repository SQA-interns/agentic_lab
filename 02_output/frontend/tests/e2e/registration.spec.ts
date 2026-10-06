import { expect, test, type Page } from "@playwright/test";
import { attachment, awaitMessage, messagesTo } from "./support/mailpit";
import { BASE_URL, ENV } from "./support/stack";
import { xlsxText } from "./support/xlsx";

const run = Date.now().toString(36);
const CONFIRMATION = "Thank you, your registration has been received.";

function basic(user: string, password: string) {
  return "Basic " + Buffer.from(`${user}:${password}`).toString("base64");
}

async function openForm(page: Page) {
  await page.goto(BASE_URL + "/");
  await expect(page.getByTestId("registration-form")).toBeVisible();
}

/** Checks the first option of a category and returns its visible name. */
async function chooseFirst(page: Page, category: string): Promise<string> {
  const box = page.getByTestId(`options-${category}`).getByRole("checkbox").first();
  await box.check();
  const id = await box.getAttribute("id");
  return (await page.locator(`label[for="${id}"]`).innerText()).trim();
}

async function confirmAndSubmit(page: Page) {
  await page.getByTestId("consent").check();
  await page.getByTestId("recaptcha").check();
  await page.getByTestId("submit").click();
}

test("AC-001-01 AC-004-01 AC-006-01 AC-007-01 NFR-01 external registration end to end", async ({
  page,
}) => {
  const email = `spela.${run}@example.si`;
  await openForm(page);
  await page.getByTestId("firstName").fill("Špela");
  await page.getByTestId("lastName").fill("Čeh Žagar");
  await page.getByTestId("email").fill(email);
  await page.getByTestId("organization").fill("Občina Škofja Loka");
  const workshop = await chooseFirst(page, "WORKSHOP");
  await confirmAndSubmit(page);

  await expect(page.getByTestId("confirmation")).toContainText(CONFIRMATION);

  const participant = await awaitMessage(email, "Špela");
  expect(participant.Text).toContain("Čeh Žagar");
  expect(participant.Text).toContain(workshop);

  const organizer = await awaitMessage(ENV.ORGANIZER_EMAILS, email);
  expect(organizer.Text).toContain("Občina Škofja Loka");
  expect(organizer.Attachments).toHaveLength(1);
  const json = JSON.parse(await attachment(organizer.ID, organizer.Attachments[0].PartID));
  expect(json.participant.firstName).toBe("Špela");
  expect(json.participant.lastName).toBe("Čeh Žagar");
  expect(json.participant.organization).toBe("Občina Škofja Loka");
  expect(json.type).toBe("EXTERNAL");
});

test("AC-002-01 AC-002-09 student registration end to end", async ({ page }) => {
  const email = `student.${run}@example.si`;
  await openForm(page);
  await page.getByTestId("type-STUDENT").check();
  await expect(page.getByTestId("organization")).toHaveCount(0);
  await page.getByTestId("firstName").fill("Luka");
  await page.getByTestId("lastName").fill("Kranjc");
  await page.getByTestId("email").fill(email);
  await page.getByTestId("studyInstitution").fill("Univerza v Ljubljani");
  await page.getByTestId("studyProgramme").fill("Računalništvo in informatika");
  await page.getByTestId("studentId").fill("63260001");
  const meal = await chooseFirst(page, "MEAL");
  await confirmAndSubmit(page);

  await expect(page.getByTestId("confirmation")).toContainText(CONFIRMATION);
  const participant = await awaitMessage(email, "Luka");
  expect(participant.Text).toContain(meal);
  const organizer = await awaitMessage(ENV.ORGANIZER_EMAILS, email);
  expect(organizer.Subject).toMatch(/^New student registration: /);
});

test("AC-001-10 AC-001-11 the form shows grouped options and an unchecked consent", async ({
  page,
}) => {
  await openForm(page);
  await expect(page.getByTestId("consent")).not.toBeChecked();
  await expect(page.getByTestId("consent-text")).not.toBeEmpty();
  for (const category of ["WORKSHOP", "EVENT", "MEAL", "OTHER"]) {
    const group = page.getByTestId(`options-${category}`);
    await expect(group).toBeVisible();
    for (const box of await group.getByRole("checkbox").all()) {
      await expect(box).not.toBeChecked();
    }
  }
});

test("AC-001-02 AC-004-02 an incomplete form shows field errors and no confirmation", async ({
  page,
}) => {
  await openForm(page);
  await page.getByTestId("firstName").fill("  ");
  await page.getByTestId("submit").click();

  await expect(page.getByTestId("error-firstName")).toHaveText("This field is required.");
  await expect(page.getByTestId("error-email")).toHaveText("This field is required.");
  await expect(page.getByTestId("error-consentGiven")).toBeVisible();
  await expect(page.getByTestId("confirmation")).toHaveCount(0);
});

test("AC-001-15 a second registration with the same email is refused", async ({ page }) => {
  const email = `twice.${run}@example.si`;
  for (const attempt of [1, 2]) {
    await openForm(page);
    await page.getByTestId("firstName").fill(`Ana${attempt}`);
    await page.getByTestId("lastName").fill("Novak");
    await page.getByTestId("email").fill(attempt === 1 ? email : email.toUpperCase());
    await page.getByTestId("organization").fill("Institut");
    await confirmAndSubmit(page);
    if (attempt === 1) {
      await expect(page.getByTestId("confirmation")).toBeVisible();
    }
  }

  await expect(page.getByTestId("error-email")).toHaveText(
    "This email address is already registered. Please contact the organizers.",
  );
  await expect(page.getByTestId("confirmation")).toHaveCount(0);
  await expect.poll(async () => (await messagesTo(email)).length, { timeout: 10_000 }).toBe(1);
});

test("AC-008-01 AC-008-02 AC-008-03 AC-008-05 organizer export through the public site", async ({
  page,
  request,
}) => {
  const email = `export.${run}@example.si`;
  await openForm(page);
  await page.getByTestId("firstName").fill("Žiga");
  await page.getByTestId("lastName").fill("Šuštaršič");
  await page.getByTestId("email").fill(email);
  await page.getByTestId("organization").fill("Fakulteta za računalništvo");
  await confirmAndSubmit(page);
  await expect(page.getByTestId("confirmation")).toBeVisible();

  const url = BASE_URL + "/api/admin/registrations/export";
  const anonymous = await request.get(url);
  expect(anonymous.status()).toBe(401);
  expect(await anonymous.text()).not.toContain(email);
  const wrong = await request.get(url, {
    headers: { Authorization: basic(ENV.ORGANIZER_USERNAME, "not-the-password") },
  });
  expect(wrong.status()).toBe(401);

  const ok = await request.get(url, {
    headers: { Authorization: basic(ENV.ORGANIZER_USERNAME, ENV.ORGANIZER_PASSWORD) },
  });
  expect(ok.status()).toBe(200);
  expect(ok.headers()["content-type"]).toContain(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  );
  const text = xlsxText(await ok.body());
  expect(text).toContain(email);
  expect(text).toContain("Žiga");
  expect(text).toContain("Šuštaršič");
  expect(text).toContain("Fakulteta za računalništvo");
});
