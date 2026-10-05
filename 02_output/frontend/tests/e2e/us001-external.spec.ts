// US-001 / US-004 end to end: an external participant registers through the page.
import { expect, test } from "@playwright/test";
import {
  confirmedRegistrationId,
  formConfig,
  giveMandatoryConsentsAndCaptcha,
  openForm,
  optionsFor,
  uniqueEmail,
} from "./support";

test("AC-001-01 AC-001-12 the external form shows its fields and unchecked consents", async ({
  page,
  request,
}) => {
  const config = await formConfig(request);
  await openForm(page);

  for (const label of [
    "First name",
    "Last name",
    "Email",
    "Organization / institution",
  ]) {
    await expect(page.getByLabel(label, { exact: true })).toBeVisible();
  }
  await expect(page.getByLabel("Student ID", { exact: true })).toHaveCount(0);
  for (const consent of config.consents) {
    await expect(
      page.getByRole("checkbox", { name: consent.text, exact: true }),
    ).not.toBeChecked();
  }
});

test("AC-001-02 AC-001-07 AC-004-01 an external participant with Slovenian characters registers and sees the confirmation", async ({
  page,
  request,
}) => {
  const config = await formConfig(request);
  const option = optionsFor(config, "external")[0];
  const email = uniqueEmail("e2e.external");
  await openForm(page);

  await page.getByLabel("First name", { exact: true }).fill("Žiga");
  await page.getByLabel("Last name", { exact: true }).fill("Čebašek");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page
    .getByLabel("Organization / institution", { exact: true })
    .fill("Šola za ščepce");
  await page.getByRole("checkbox", { name: option.name, exact: true }).check();
  await giveMandatoryConsentsAndCaptcha(page, config);
  await page.getByRole("button", { name: "Register" }).click();

  await confirmedRegistrationId(page);
  const confirmation = page.getByRole("status");
  await expect(confirmation).toContainText("Žiga Čebašek");
  await expect(confirmation).toContainText(email);
  await expect(confirmation).toContainText(option.name);
});

test("AC-001-03 an empty required field is reported next to it and nothing is confirmed", async ({
  page,
  request,
}) => {
  const config = await formConfig(request);
  await openForm(page);

  await page.getByLabel("First name", { exact: true }).fill("Ana");
  await page
    .getByLabel("Email", { exact: true })
    .fill(uniqueEmail("e2e.empty"));
  await page
    .getByLabel("Organization / institution", { exact: true })
    .fill("Institut");
  await giveMandatoryConsentsAndCaptcha(page, config);
  await page.getByRole("button", { name: "Register" }).click();

  const lastName = page.getByLabel("Last name", { exact: true });
  await expect(lastName).toHaveAttribute("aria-invalid", "true");
  const errorId = (await lastName.getAttribute("aria-describedby")) ?? "";
  await expect(page.locator(`[id="${errorId.split(" ")[0]}"]`)).toHaveText(
    "This field is required.",
  );
  await expect(page.getByRole("status")).toHaveCount(0);
});
