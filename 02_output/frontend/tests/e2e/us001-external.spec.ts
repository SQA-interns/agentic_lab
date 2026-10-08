// US-001 External participant registration, end to end against a fresh local stack.
import { expect, test } from "@playwright/test";
import { fillExternal, organizerCopy, uniqueEmail } from "./helpers";
import { messagesTo } from "./mailpit";
import { loadState } from "./stack";

test("AC-001-01 AC-006-01 AC-007-01 AC-007-02 external registration with Slovenian text end to end", async ({
  page,
}) => {
  const email = uniqueEmail("e2e-ext");
  await fillExternal(page, email);
  await page.getByTestId("submit").click();

  await expect(page.getByTestId("confirmation")).toHaveText(
    "Thank you, your registration was received.",
  );

  await expect
    .poll(async () => (await messagesTo(email, email)).length, { timeout: 30_000 })
    .toBe(1);
  const [participantMail] = await messagesTo(email, email);
  expect(participantMail.Subject).toBe("Registration confirmed: E2E Konferenca");
  expect(participantMail.Text).toContain("Čedomir");
  expect(participantMail.Text).toContain("Šuštaršič");

  expect(loadState().organizerEmail).toBeTruthy();
  const copy = await organizerCopy(email);
  expect(copy.type).toBe("EXTERNAL");
  expect(copy.participant).toMatchObject({
    firstName: "Čedomir",
    lastName: "Šuštaršič",
    email,
    organization: "Žalec d.o.o.",
  });
});

test("AC-001-04 AC-001-05 no-break spaces are whitespace in the browser and the backend", async ({
  page,
  request,
}) => {
  const email = uniqueEmail("e2e-nbsp");
  await fillExternal(page, email);
  await page.getByTestId("field-lastName").fill("  ");
  await page.getByTestId("submit").click();
  await expect(page.getByTestId("error-lastName")).toBeVisible();
  await expect(page.getByTestId("confirmation")).toHaveCount(0);

  await page.getByTestId("field-lastName").fill(" Novak ");
  await page.getByTestId("submit").click();
  await expect(page.getByTestId("confirmation")).toBeVisible();

  const copy = await organizerCopy(email);
  expect(copy.participant.lastName).toBe("Novak");

  const blank = await request.post("/api/registrations", {
    data: {
      type: "EXTERNAL",
      firstName: " ",
      lastName: "Novak",
      email: uniqueEmail("e2e-nbsp-api"),
      organization: "Org",
      optionIds: [],
      consentIds: ["data-processing"],
      recaptchaToken: "test-mode-pass",
    },
  });
  expect(blank.status()).toBe(400);
});

test("AC-001-12 the consent checkbox is not preselected", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByTestId("consent-data-processing")).not.toBeChecked();
});
