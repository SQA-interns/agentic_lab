import { expect, test } from "@playwright/test";
import {
  confirmNotARobot,
  exportWorkbook,
  external,
  fill,
  jsonCopies,
  mails,
  openForm,
  submit,
  tick,
  ui,
  uniqueEmail,
  workbookText,
} from "./support/form";

test.describe("US-001 External participant registration", () => {
  test("AC-001-01 the external form asks for exactly the external fields", async ({
    page,
  }) => {
    await openForm(page, "EXTERNAL");

    for (const name of ["firstName", "lastName", "email", "organization"]) {
      await expect(page.getByTestId(ui.field(name))).toBeVisible();
    }
    for (const name of ["studyInstitution", "studyProgramme", "studentId"]) {
      await expect(page.getByTestId(ui.field(name))).toHaveCount(0);
    }
    await expect(page.getByLabel("Organization / institution")).toBeVisible();
  });

  test("AC-001-02 AC-004-01 NFR-01 a valid external registration with Slovenian characters is confirmed, stored, copied, emailed and exported unchanged", async ({
    page,
  }) => {
    const email = uniqueEmail();
    await openForm(page, "EXTERNAL");
    await fill(page, {
      firstName: "Čedomir Žiga",
      lastName: "Šušteršič",
      email,
      organization: "Fakulteta za računalništvo",
    });
    await tick(page, ui.option("ws-testing-ai"));
    await tick(page, ui.option("ev-gala-dinner"));
    await tick(page, ui.consent("data-processing"));
    await confirmNotARobot(page);
    await submit(page);

    await expect(page.getByTestId(ui.confirmation)).toBeVisible();
    await expect(page.getByTestId(ui.confirmation)).toContainText(
      "Registration received",
    );

    const copy = jsonCopies().find((c) => c.includes(email));
    expect(copy).toBeDefined();
    expect(copy).toContain("Čedomir Žiga");
    expect(copy).toContain("Šušteršič");
    const messages = await mails(email, 2);
    const participant = messages.find((m) =>
      JSON.stringify(m["To"]).includes(email),
    );
    expect(String(participant?.["Text"])).toContain("Čedomir Žiga");
    expect(String(participant?.["Text"])).toContain("Šušteršič");
    const workbook = await exportWorkbook();
    expect(workbook.status).toBe(200);
    const text = workbookText(workbook.bytes);
    expect(text).toContain("Čedomir Žiga");
    expect(text).toContain("Šušteršič");
    expect(text).toContain("Gala večerja");
  });

  test("AC-001-04 NFR-03 an empty required field is reported next to the field and nothing is confirmed", async ({
    page,
  }) => {
    await openForm(page, "EXTERNAL");
    await fill(page, { ...external(uniqueEmail()), lastName: "" });
    await tick(page, ui.consent("data-processing"));
    await confirmNotARobot(page);
    await submit(page);

    await expect(page.getByTestId(ui.error("lastName"))).toHaveText(
      ui.messages.REQUIRED,
    );
    await expect(page.getByTestId(ui.confirmation)).toHaveCount(0);
  });

  test("AC-001-05 KP-03 a required field with only no-break spaces is reported as required", async ({
    page,
  }) => {
    await openForm(page, "EXTERNAL");
    await fill(page, { ...external(uniqueEmail()), firstName: "  " });
    await tick(page, ui.consent("data-processing"));
    await confirmNotARobot(page);
    await submit(page);

    await expect(page.getByTestId(ui.error("firstName"))).toHaveText(
      ui.messages.REQUIRED,
    );
    await expect(page.getByTestId(ui.confirmation)).toHaveCount(0);
  });

  test("AC-001-09 only active options for external participants are offered, grouped by category", async ({
    page,
  }) => {
    await openForm(page, "EXTERNAL");

    const workshops = page.getByTestId(ui.category("WORKSHOP"));
    await expect(workshops).toContainText("Workshops");
    await expect(
      workshops.getByTestId(ui.option("ws-testing-ai")),
    ).toBeVisible();
    await expect(
      workshops.getByTestId(ui.option("ws-industry-masterclass")),
    ).toBeVisible();
    await expect(page.getByTestId(ui.option("ws-retired"))).toHaveCount(0);
    await expect(
      page
        .getByTestId(ui.category("EVENT"))
        .getByTestId(ui.option("ev-gala-dinner")),
    ).toBeVisible();
    await expect(
      page
        .getByTestId(ui.category("MEAL"))
        .getByTestId(ui.option("meal-lunch-day1")),
    ).toBeVisible();
    await expect(
      page
        .getByTestId(ui.category("OTHER"))
        .getByTestId(ui.option("other-city-tour")),
    ).toBeVisible();
  });

  test("AC-001-13 no consent is preselected", async ({ page }) => {
    await openForm(page, "EXTERNAL");

    await expect(
      page.getByTestId(ui.consent("data-processing")),
    ).not.toBeChecked();
    await expect(page.getByTestId(ui.consent("photo"))).not.toBeChecked();
  });

  test("AC-001-14 a missing mandatory consent is identified and nothing is confirmed", async ({
    page,
  }) => {
    await openForm(page, "EXTERNAL");
    await fill(page, external(uniqueEmail()));
    await confirmNotARobot(page);
    await submit(page);

    await expect(
      page.getByTestId("error-consent-data-processing"),
    ).toBeVisible();
    await expect(page.getByTestId(ui.confirmation)).toHaveCount(0);
  });

  test("AC-001-16 a submission without the anti-automation check is not confirmed", async ({
    page,
  }) => {
    const email = uniqueEmail();
    await openForm(page, "EXTERNAL");
    await fill(page, external(email));
    await tick(page, ui.consent("data-processing"));
    await submit(page);

    await expect(page.getByTestId("error-recaptchaToken")).toHaveText(
      ui.messages.CAPTCHA_FAILED,
    );
    await expect(page.getByTestId(ui.confirmation)).toHaveCount(0);
    expect(jsonCopies().some((c) => c.includes(email))).toBe(false);
  });
});
