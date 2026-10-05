// US-002 / US-003 end to end: a student registers through the page and sees only student options.
import { expect, test } from "@playwright/test";
import {
  confirmedRegistrationId,
  formConfig,
  giveMandatoryConsentsAndCaptcha,
  openForm,
  optionsFor,
  uniqueEmail,
} from "./support";

test("AC-002-01 AC-003-03 the student form shows the student fields and only options offered to students", async ({
  page,
  request,
}) => {
  const config = await formConfig(request);
  await openForm(page);

  await page.getByRole("radio", { name: "Student" }).check();

  for (const label of [
    "First name",
    "Last name",
    "Email",
    "Study institution",
    "Study programme",
    "Student ID",
  ]) {
    await expect(page.getByLabel(label, { exact: true })).toBeVisible();
  }
  await expect(
    page.getByLabel("Organization / institution", { exact: true }),
  ).toHaveCount(0);
  for (const option of config.options) {
    const checkbox = page.getByRole("checkbox", {
      name: option.name,
      exact: true,
    });
    if (option.offeredTo.includes("student")) {
      await expect(checkbox).toBeVisible();
    } else {
      await expect(checkbox).toHaveCount(0);
    }
  }
});

test("AC-002-02 AC-002-05 a student with Slovenian study data registers and sees the confirmation", async ({
  page,
  request,
}) => {
  const config = await formConfig(request);
  const option = optionsFor(config, "student")[0];
  const email = uniqueEmail("e2e.student");
  await openForm(page);

  await page.getByRole("radio", { name: "Student" }).check();
  await page.getByLabel("First name", { exact: true }).fill("Luka");
  await page.getByLabel("Last name", { exact: true }).fill("Kranjc");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page
    .getByLabel("Study institution", { exact: true })
    .fill("Univerza v Ljubljani");
  await page
    .getByLabel("Study programme", { exact: true })
    .fill("Računalništvo in informatika");
  await page.getByLabel("Student ID", { exact: true }).fill("63210001");
  await page.getByRole("checkbox", { name: option.name, exact: true }).check();
  await giveMandatoryConsentsAndCaptcha(page, config);
  await page.getByRole("button", { name: "Register" }).click();

  await confirmedRegistrationId(page);
  await expect(page.getByRole("status")).toContainText("Luka Kranjc");
  await expect(page.getByRole("status")).toContainText(option.name);
});
