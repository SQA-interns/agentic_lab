import { expect, test, type Page } from "@playwright/test";

// Acceptance tests (Phase 3, frozen) for the UI-level criteria. They drive the
// application through the browser as specified in docs/specification.md §12
// against a running stack (frontend on baseURL, backend on /api in reCAPTCHA
// test mode). They are data-agnostic about the configured options: the
// expected options are read from GET /api/options (docs/contracts/openapi.yaml).

type Option = {
  id: string;
  category: "WORKSHOP" | "EVENT" | "MEAL" | "OTHER";
  name: string;
};

const HEADINGS: Record<Option["category"], string> = {
  WORKSHOP: "Workshops",
  EVENT: "Events",
  MEAL: "Meals",
  OTHER: "Other activities",
};

function uniqueEmail(prefix: string): string {
  return `${prefix}.${Date.now()}.${Math.floor(Math.random() * 1e6)}@example.si`;
}

async function chooseType(
  page: Page,
  type: "External participant" | "Student",
) {
  await page.getByRole("radio", { name: type, exact: true }).check();
}

function consent(page: Page) {
  return page.getByRole("checkbox", {
    name: /processing of my personal data/i,
  });
}

async function completeCaptcha(page: Page) {
  await page.getByRole("checkbox", { name: "I am not a robot" }).check();
}

async function submit(page: Page) {
  await page.getByRole("button", { name: "Register" }).click();
}

async function activeOptions(page: Page): Promise<Option[]> {
  const res = await page.request.get("/api/options");
  expect(res.status()).toBe(200);
  return (await res.json()) as Option[];
}

test.beforeEach(async ({ page }) => {
  await page.goto("/");
});

test("AC-001-08: mandatory consent is not preselected on the external form", async ({
  page,
}) => {
  await chooseType(page, "External participant");
  await expect(consent(page)).toBeVisible();
  await expect(consent(page)).not.toBeChecked();
});

test("AC-002-05: mandatory consent is not preselected on the student form", async ({
  page,
}) => {
  await chooseType(page, "Student");
  await expect(consent(page)).toBeVisible();
  await expect(consent(page)).not.toBeChecked();
});

test("AC-001-09 / AC-003-01: active options are offered grouped by set", async ({
  page,
}) => {
  const options = await activeOptions(page);
  expect(options.length).toBeGreaterThan(0);
  await chooseType(page, "External participant");

  for (const option of options) {
    const group = page.getByRole("group", {
      name: HEADINGS[option.category],
      exact: true,
    });
    await expect(group).toBeVisible();
    await expect(
      group.getByRole("checkbox", { name: option.name, exact: true }),
    ).toBeVisible();
  }
  const offered = page.locator(
    'fieldset[data-option-group] input[type="checkbox"]',
  );
  await expect(offered).toHaveCount(options.length);
});

test("AC-002-08: each form shows only its own fields", async ({ page }) => {
  await chooseType(page, "External participant");
  await expect(
    page.getByLabel("Organization / institution", { exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Student ID", { exact: true })).toHaveCount(0);

  await chooseType(page, "Student");
  await expect(
    page.getByLabel("Study institution", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByLabel("Study programme", { exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Student ID", { exact: true })).toBeVisible();
  await expect(
    page.getByLabel("Organization / institution", { exact: true }),
  ).toHaveCount(0);
});

test("AC-001-01 / AC-004-01: external registration shows a confirmation after success", async ({
  page,
}) => {
  const options = await activeOptions(page);
  const email = uniqueEmail("e2e.external");
  await chooseType(page, "External participant");
  await page.getByLabel("First name", { exact: true }).fill("Živa");
  await page.getByLabel("Last name", { exact: true }).fill("Čepič");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page
    .getByLabel("Organization / institution", { exact: true })
    .fill("Univerza v Ljubljani");
  await page
    .getByRole("checkbox", { name: options[0].name, exact: true })
    .check();
  await consent(page).check();
  await completeCaptcha(page);

  const response = page.waitForResponse(
    (r) =>
      r.url().endsWith("/api/registrations") && r.request().method() === "POST",
  );
  await submit(page);
  expect((await response).status()).toBe(201);

  const confirmation = page.getByRole("heading", {
    name: "Registration received",
  });
  await expect(confirmation).toBeVisible();
  await expect(page.getByText(email)).toBeVisible();
  await expect(page.getByText("Živa Čepič")).toBeVisible();
  await expect(page.getByText(options[0].name)).toBeVisible();
});

test("AC-002-01 / AC-004-01: student registration shows a confirmation after success", async ({
  page,
}) => {
  const email = uniqueEmail("e2e.student");
  await chooseType(page, "Student");
  await page.getByLabel("First name", { exact: true }).fill("Luka");
  await page.getByLabel("Last name", { exact: true }).fill("Šinkovec");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page
    .getByLabel("Study institution", { exact: true })
    .fill("Fakulteta za računalništvo");
  await page
    .getByLabel("Study programme", { exact: true })
    .fill("Računalništvo in informatika");
  await page.getByLabel("Student ID", { exact: true }).fill("63200001");
  await consent(page).check();
  await completeCaptcha(page);
  await submit(page);

  await expect(
    page.getByRole("heading", { name: "Registration received" }),
  ).toBeVisible();
  await expect(page.getByText(email)).toBeVisible();
});

test("AC-004-02: invalid input shows reasons and no confirmation", async ({
  page,
}) => {
  let posted = false;
  page.on("request", (r) => {
    if (r.url().endsWith("/api/registrations") && r.method() === "POST")
      posted = true;
  });
  await chooseType(page, "External participant");
  await page.getByLabel("First name", { exact: true }).fill("Ana");
  await page.getByLabel("Email", { exact: true }).fill("ana.novak");
  await submit(page);

  await expect(page.getByText("Enter a valid email address.")).toBeVisible();
  await expect(page.getByLabel("Email", { exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await expect(page.getByLabel("Last name", { exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await expect(
    page.getByRole("heading", { name: "Registration received" }),
  ).toHaveCount(0);
  expect(posted).toBe(false);
});

test("AC-004-02: server-side rejection is shown next to the field, without confirmation", async ({
  page,
}) => {
  await page.route("**/api/registrations", (route) =>
    route.fulfill({
      status: 400,
      contentType: "application/problem+json",
      body: JSON.stringify({
        title: "Validation failed",
        status: 400,
        code: "VALIDATION_FAILED",
        errors: [
          {
            field: "email",
            code: "INVALID_EMAIL",
            message: "Enter a valid email address.",
          },
        ],
      }),
    }),
  );
  await chooseType(page, "External participant");
  await page.getByLabel("First name", { exact: true }).fill("Ana");
  await page.getByLabel("Last name", { exact: true }).fill("Novak");
  await page
    .getByLabel("Email", { exact: true })
    .fill(uniqueEmail("e2e.server400"));
  await page
    .getByLabel("Organization / institution", { exact: true })
    .fill("IJS");
  await consent(page).check();
  await completeCaptcha(page);
  await submit(page);

  await expect(page.getByText("Enter a valid email address.")).toBeVisible();
  await expect(page.getByLabel("Email", { exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await expect(
    page.getByRole("heading", { name: "Registration received" }),
  ).toHaveCount(0);
});

test("AC-004-03: no confirmation when the registration could not be stored", async ({
  page,
}) => {
  await page.route("**/api/registrations", (route) =>
    route.fulfill({
      status: 500,
      contentType: "application/problem+json",
      body: JSON.stringify({
        title: "Registration not saved",
        status: 500,
        code: "REGISTRATION_NOT_SAVED",
      }),
    }),
  );
  await chooseType(page, "External participant");
  await page.getByLabel("First name", { exact: true }).fill("Ana");
  await page.getByLabel("Last name", { exact: true }).fill("Novak");
  await page
    .getByLabel("Email", { exact: true })
    .fill(uniqueEmail("e2e.server500"));
  await page
    .getByLabel("Organization / institution", { exact: true })
    .fill("IJS");
  await consent(page).check();
  await completeCaptcha(page);
  await submit(page);

  await expect(
    page.getByRole("alert").filter({
      hasText: "Registration could not be completed. Please try again later.",
    }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Registration received" }),
  ).toHaveCount(0);
});
