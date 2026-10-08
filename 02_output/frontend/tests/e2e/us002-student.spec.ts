// US-002 Student registration, end to end against a fresh local stack.
import { expect, test } from "@playwright/test";
import { uniqueEmail } from "./helpers";

test("AC-002-01 student registration end to end", async ({ page }) => {
  const email = uniqueEmail("e2e-stu");
  await page.goto("/");
  await page.getByTestId("type-student").check();
  await expect(page.getByTestId("field-organization")).toHaveCount(0);
  await page.getByTestId("field-firstName").fill("Žiga");
  await page.getByTestId("field-lastName").fill("Kovač");
  await page.getByTestId("field-email").fill(email);
  await page.getByTestId("field-studyInstitution").fill("Univerza v Ljubljani");
  await page.getByTestId("field-studyProgramme").fill("Računalništvo in informatika");
  await page.getByTestId("field-studentId").fill("63210001");
  await page.getByTestId("option-ev-career-fair").check();
  await page.getByTestId("consent-data-processing").check();
  await page.getByTestId("recaptcha-test").check();
  await page.getByTestId("submit").click();

  await expect(page.getByTestId("confirmation")).toBeVisible();
});
