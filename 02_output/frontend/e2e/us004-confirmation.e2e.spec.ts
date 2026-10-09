import { expect, test } from "@playwright/test";
import {
  confirmNotARobot,
  external,
  fill,
  openForm,
  submit,
  tick,
  ui,
  uniqueEmail,
} from "./support/form";

async function registerExternal(
  page: import("@playwright/test").Page,
  email: string,
) {
  await openForm(page, "EXTERNAL");
  await fill(page, external(email));
  await tick(page, ui.consent("data-processing"));
  await confirmNotARobot(page);
  await submit(page);
}

test.describe("US-004 Registration confirmation", () => {
  test("AC-004-01 an accepted registration shows the confirmation instead of the form", async ({
    page,
  }) => {
    await registerExternal(page, uniqueEmail());

    await expect(page.getByTestId(ui.confirmation)).toContainText(
      "Registration received",
    );
    await expect(page.getByTestId(ui.submit)).toHaveCount(0);
  });

  test("AC-004-02 a registration rejected by the server shows the reason and no confirmation", async ({
    page,
  }) => {
    const email = uniqueEmail();
    await registerExternal(page, email);
    await expect(page.getByTestId(ui.confirmation)).toBeVisible();

    await registerExternal(page, email);

    await expect(page.getByTestId(ui.error("email"))).toHaveText(
      ui.messages.ALREADY_REGISTERED,
    );
    await expect(page.getByTestId(ui.confirmation)).toHaveCount(0);
  });

  test("AC-004-03 a storage failure shows a general error and no confirmation", async ({
    page,
  }) => {
    await page.route("**/api/registrations", (route) =>
      route.fulfill({
        status: 503,
        contentType: "application/problem+json",
        body: JSON.stringify({ title: "Service Unavailable", status: 503 }),
      }),
    );

    await registerExternal(page, uniqueEmail());

    await expect(page.getByTestId(ui.formError)).toHaveText(
      ui.messages.GENERAL,
    );
    await expect(page.getByTestId(ui.confirmation)).toHaveCount(0);
  });
});
